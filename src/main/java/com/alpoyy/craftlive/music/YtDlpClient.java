package com.alpoyy.craftlive.music;

import com.alpoyy.craftlive.CraftLive;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Wrapper around a user-installed yt-dlp binary (authorized by the project
 * owner for the music platform).
 *
 * AVAILABILITY — the critical design points (fixes the 5-FPS bug):
 *  - The probe NEVER runs on the render/main thread; it runs on this
 *    class's single background executor, throttled.
 *  - The result (success AND failure) is cached in a state machine:
 *    UNKNOWN → CHECKING → AVAILABLE / UNAVAILABLE.
 *  - Render code only reads the cached state via {@link #availability()} —
 *    it can never spawn a process per frame.
 *  - Candidates: the user-configured path, then "yt-dlp", "yt-dlp_linux"
 *    and "python3 -m yt_dlp" (covers Android/Termux and aarch64 setups).
 *  - A "recheck" re-probes on demand.
 *
 * The binary is NEVER bundled with the mod; if it is missing we report a
 * clear, honest error and the UI offers handoff links instead.
 */
public class YtDlpClient {
    public enum Availability { UNKNOWN, CHECKING, AVAILABLE, UNAVAILABLE }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CraftLive-YtDlp");
        t.setDaemon(true);
        return t;
    });

    private volatile String binaryPath = "";
    private volatile Availability availability = Availability.UNKNOWN;
    private volatile String version = null;
    private volatile String[] commandBase = null;   // winning candidate argv
    private final AtomicLong lastProbeMs = new AtomicLong(0);
    private static final long PROBE_THROTTLE_MS = 3000;

    public void setBinaryPath(String path) {
        this.binaryPath = (path == null || path.isBlank()) ? "" : path.trim();
        resetAvailability();
    }

    public String binaryPath() {
        return binaryPath;
    }

    // ---- availability state machine ----

    public Availability availability() {
        return availability;
    }

    public boolean isAvailable() {
        return availability == Availability.AVAILABLE;
    }

    public String availableVersion() {
        return version;
    }

    /** Reset cached state (used when the user changes the path). */
    public void resetAvailability() {
        availability = Availability.UNKNOWN;
        version = null;
        commandBase = null;
    }

    /**
     * Probe asynchronously (throttled). Safe to call from render code:
     * it only flips state and schedules background work.
     */
    public void probeAsync() {
        long now = System.currentTimeMillis();
        long last = lastProbeMs.get();
        if (now - last < PROBE_THROTTLE_MS) return;
        if (!lastProbeMs.compareAndSet(last, now)) return;

        availability = Availability.CHECKING;
        executor.execute(this::probeSync);
    }

    /** Runs on the executor thread only. */
    private void probeSync() {
        for (String[] candidate : commandCandidates()) {
            try {
                List<String> cmd = new ArrayList<>(List.of(candidate));
                cmd.add("--version");
                Process p = new ProcessBuilder(cmd).start();
                boolean done = p.waitFor(6, TimeUnit.SECONDS);
                if (done && p.exitValue() == 0) {
                    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                    version = out.split("\\s+")[0];
                    commandBase = candidate;
                    availability = Availability.AVAILABLE;
                    CraftLive.LOGGER.info("yt-dlp found: {} ({})", version, candidate[0]);
                    return;
                }
                p.destroyForcibly();
            } catch (Exception e) {
                CraftLive.LOGGER.debug("yt-dlp probe failed for {}: {}", candidate[0], e.toString());
            }
        }
        version = null;
        commandBase = null;
        availability = Availability.UNAVAILABLE;
        CraftLive.LOGGER.info("yt-dlp not found (probed all candidates)");
    }

    /**
     * Candidate commands to probe, in order. Pure + testable.
     * Each entry is an argv prefix (e.g. ["python3","-m","yt_dlp"]).
     */
    public List<String[]> commandCandidates() {
        List<String[]> candidates = new ArrayList<>();
        if (binaryPath != null && !binaryPath.isBlank()) {
            candidates.add(binaryPath.split("\\s+"));
        }
        candidates.add(new String[]{"yt-dlp"});
        candidates.add(new String[]{"yt-dlp_linux"});
        candidates.add(new String[]{"python3", "-m", "yt_dlp"});
        candidates.add(new String[]{"python", "-m", "yt_dlp"});
        return candidates;
    }

    /** argv prefix of the working yt-dlp invocation, or null when unavailable. */
    public String[] baseCommand() {
        return commandBase;
    }

    /** Result of a search. */
    public static class SearchResult {
        public final List<Track> tracks;
        public final String error; // null on success

        public SearchResult(List<Track> tracks, String error) {
            this.tracks = tracks;
            this.error = error;
        }
    }

    /** Search YouTube (ytsearch). Async; callback on executor thread. */
    public CompletableFuture<SearchResult> search(String query, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isAvailable()) {
                return new SearchResult(List.of(), unavailableMessage());
            }
            try {
                List<String> cmd = new ArrayList<>(List.of(commandBase));
                cmd.addAll(List.of(
                        "--dump-json", "--flat-playlist", "--no-warnings",
                        "--quiet", "--no-progress",
                        "ytsearch" + Math.max(1, Math.min(20, limit)) + ":" + query));
                Process p = new ProcessBuilder(cmd).start();
                boolean done = p.waitFor(25, TimeUnit.SECONDS);
                if (!done) {
                    p.destroyForcibly();
                    return new SearchResult(List.of(), "Timeout saat mencari lagu.");
                }
                if (p.exitValue() != 0) {
                    return new SearchResult(List.of(), "yt-dlp gagal (exit " + p.exitValue() + ").");
                }
                List<Track> tracks = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;
                        try {
                            JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                            Track t = trackFromFlatJson(json);
                            if (t != null) tracks.add(t);
                        } catch (Exception ignored) {
                        }
                    }
                }
                return new SearchResult(tracks, null);
            } catch (Exception e) {
                return new SearchResult(List.of(), "Pencarian gagal: " + e.getClass().getSimpleName());
            }
        }, executor);
    }

    private Track trackFromFlatJson(JsonObject json) {
        String id = optString(json, "id");
        String title = optString(json, "title");
        if (id == null || title == null) return null;
        String url = optString(json, "url");
        if (url == null || url.isBlank()) {
            url = "https://www.youtube.com/watch?v=" + id;
        }
        Track t = new Track(title,
                optString(json, "uploader") == null ? "" : optString(json, "uploader"),
                null,
                optLong(json, "duration", -1) * 1000L,
                "YouTube Music");
        t.trackUrl = url;
        JsonElement best = json.get("thumbnails");
        if (best != null && best.isJsonArray()) {
            JsonArray arr = best.getAsJsonArray();
            if (arr.size() > 0) {
                String thumb = optString(arr.get(arr.size() - 1).getAsJsonObject(), "url");
                t.artworkUrl = thumb;
            }
        }
        return t;
    }

    /** Resolve a playable direct audio URL. Async. */
    public CompletableFuture<Resolved> resolveStream(String videoUrl) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isAvailable()) {
                return new Resolved(null, unavailableMessage());
            }
            try {
                List<String> cmd = new ArrayList<>(List.of(commandBase));
                cmd.addAll(List.of(
                        "-f", "bestaudio/best",
                        "--no-warnings", "--quiet", "--no-progress", "--no-playlist",
                        "-g",
                        videoUrl));
                Process p = new ProcessBuilder(cmd).start();
                boolean done = p.waitFor(30, TimeUnit.SECONDS);
                if (!done) {
                    p.destroyForcibly();
                    return new Resolved(null, "Timeout saat menyiapkan audio.");
                }
                if (p.exitValue() != 0) {
                    String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    String msg = err.isBlank() ? "yt-dlp gagal (exit " + p.exitValue() + ")" : firstLine(err);
                    return new Resolved(null, msg);
                }
                String url = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        .lines().filter(l -> !l.isBlank()).findFirst().orElse(null);
                if (url == null) {
                    return new Resolved(null, "Tidak ada audio stream yang tersedia.");
                }
                return new Resolved(url, null);
            } catch (Exception e) {
                return new Resolved(null, "Gagal menyiapkan audio: " + e.getClass().getSimpleName());
            }
        }, executor);
    }

    private static String firstLine(String s) {
        String t = s.trim();
        int nl = t.indexOf('\n');
        return nl > 0 ? t.substring(0, nl) : t;
    }

    /** Clear, actionable message for UI display. */
    public String unavailableMessage() {
        return "yt-dlp tidak ditemukan. Instal yt-dlp (atau 'pip install yt-dlp' dengan Python), "
                + "lalu klik Periksa Ulang atau atur path di Settings → Music Platforms.";
    }

    private static String optString(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static long optLong(JsonObject o, String key, long def) {
        JsonElement e = o.get(key);
        try {
            return e == null || e.isJsonNull() ? def : e.getAsLong();
        } catch (Exception ex) {
            return def;
        }
    }

    public static class Resolved {
        public final String streamUrl;
        public final String error;

        public Resolved(String streamUrl, String error) {
            this.streamUrl = streamUrl;
            this.error = error;
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
