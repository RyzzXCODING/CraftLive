package com.alpoyy.craftlive.music;

import com.alpoyy.craftlive.CraftLive;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Wrapper around a user-installed yt-dlp binary (authorized by the project
 * owner for the music platform).
 *
 * Capabilities:
 *  - search YouTube Music: {@code ytsearch<n>:<query>} flat playlist dump
 *  - resolve a direct audio stream URL for playback
 *  - fetch metadata for a single video
 *
 * The binary is NEVER bundled with the mod; if it is missing we report a
 * clear, honest error and the UI offers handoff links instead.
 * All commands run asynchronously off the Minecraft thread with timeouts.
 */
public class YtDlpClient {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CraftLive-YtDlp");
        t.setDaemon(true);
        return t;
    });

    private volatile String binaryPath = "yt-dlp";
    private volatile String cachedVersion = null;

    public void setBinaryPath(String path) {
        this.binaryPath = (path == null || path.isBlank()) ? "yt-dlp" : path.trim();
        this.cachedVersion = null;
    }

    public String binaryPath() {
        return binaryPath;
    }

    /** Check availability once; result cached. Returns version or null. */
    public String checkAvailable() {
        if (cachedVersion != null) return cachedVersion;
        try {
            Process p = new ProcessBuilder(binaryPath, "--version").start();
            boolean done = p.waitFor(8, TimeUnit.SECONDS);
            if (done && p.exitValue() == 0) {
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                cachedVersion = out.split("\\s+")[0];
                return cachedVersion;
            }
            p.destroyForcibly();
        } catch (Exception e) {
            CraftLive.LOGGER.info("yt-dlp not available: {}", e.toString());
        }
        return null;
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
            String available = checkAvailable();
            if (available == null) {
                return new SearchResult(List.of(), "yt-dlp tidak ditemukan. Instal yt-dlp lalu atur path-nya di Settings → Music Platforms.");
            }
            try {
                List<String> cmd = List.of(
                        binaryPath,
                        "--dump-json", "--flat-playlist", "--no-warnings",
                        "--quiet", "--no-progress",
                        "ytsearch" + Math.max(1, Math.min(20, limit)) + ":" + query);
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
            String available = checkAvailable();
            if (available == null) {
                return new Resolved(null, "yt-dlp tidak ditemukan.");
            }
            try {
                List<String> cmd = List.of(
                        binaryPath,
                        "-f", "bestaudio/best",
                        "--no-warnings", "--quiet", "--no-progress", "--no-playlist",
                        "-g",
                        videoUrl);
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
