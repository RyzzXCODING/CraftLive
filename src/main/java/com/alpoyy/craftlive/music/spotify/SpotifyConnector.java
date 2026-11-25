package com.alpoyy.craftlive.music.spotify;

import com.alpoyy.craftlive.CraftLive;
import com.alpoyy.craftlive.config.SecureStore;
import com.alpoyy.craftlive.music.MusicPlatform;
import com.alpoyy.craftlive.music.PlayerState;
import com.alpoyy.craftlive.music.Track;
import com.alpoyy.craftlive.util.BrowserOpen;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Spotify integration through the OFFICIAL Spotify Web API only:
 *  - OAuth 2.0 Authorization Code flow with PKCE (never passwords, never cookies)
 *  - currently-playing metadata (title, artist, album, art, progress)
 *  - remote play/pause/next/previous + volume when the user has Premium
 *    and an active Spotify session (permitted platform automation)
 *  - handoff (open track in browser)
 *
 * Tokens are stored via {@link SecureStore} and never logged.
 * The user supplies their own Spotify app's Client ID (not a secret) in
 * Settings; the redirect goes to http://127.0.0.1:47133/callback.
 */
public class SpotifyConnector implements MusicPlatform {
    public static final String AUTH_URL = "https://accounts.spotify.com/authorize";
    public static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    public static final String API = "https://api.spotify.com/v1";
    public static final int REDIRECT_PORT = 47133;
    public static final String REDIRECT_URI = "http://127.0.0.1:" + REDIRECT_PORT + "/callback";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final PlayerState state = new PlayerState();
    private final AtomicReference<String> accessToken = new AtomicReference<>(null);
    private final AtomicReference<String> refreshToken = new AtomicReference<>(null);
    private final AtomicReference<String> pkceVerifier = new AtomicReference<>(null);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CraftLive-Spotify");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean polling = false;
    private volatile String clientId = "";
    private volatile Consumer<String> errorHandler = e -> {};

    public SpotifyConnector() {
        state.controlsSupported = true;   // when connected with Premium account
        state.volumeSupported = true;
    }

    public void setErrorHandler(Consumer<String> handler) {
        this.errorHandler = handler == null ? e -> {} : handler;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    public String clientId() {
        return clientId;
    }

    @Override
    public String id() {
        return "spotify";
    }

    @Override
    public String displayName() {
        return "Spotify";
    }

    @Override
    public PlayerState state() {
        return state;
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && accessToken.get() != null;
    }

    // ---- OAuth PKCE ----

    private static String pkceCodeVerifier() {
        byte[] bytes = new byte[48];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String pkceChallenge(String verifier) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Start the OAuth flow: opens the browser, runs a tiny local server for the redirect. */
    public void beginAuthorization() {
        if (clientId.isBlank()) {
            errorHandler.accept("Client ID Spotify belum diisi (Settings → Music Platforms).");
            return;
        }
        Thread t = new Thread(this::runAuthorizationFlow, "CraftLive-SpotifyAuth");
        t.setDaemon(true);
        t.start();
    }

    private void runAuthorizationFlow() {
        try {
            String verifier = pkceCodeVerifier();
            pkceVerifier.set(verifier);
            String challenge = pkceChallenge(verifier);

            String url = AUTH_URL + "?response_type=code"
                    + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode(
                    "user-read-currently-playing user-read-playback-state user-modify-playback-state streaming",
                    StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                    + "&code_challenge_method=S256"
                    + "&code_challenge=" + challenge;

            HttpServer server = HttpServer.create(new InetSocketAddress(REDIRECT_PORT), 0);
            final String[] authCode = {null};
            server.createContext("/callback", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                if (query != null) {
                    for (String pair : query.split("&")) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2 && kv[0].equals("code")) {
                            authCode[0] = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        }
                    }
                }
                byte[] body = ("<html><body style='background:#171719;color:#F1F1F1;font-family:sans-serif'>"
                        + "<h2>CraftLive × Spotify</h2><p>Autorisasi diterima — kembalilah ke Minecraft.</p></body></html>")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            BrowserOpen.open(URI.create(url));

            // wait up to 3 minutes for the redirect
            long deadline = System.currentTimeMillis() + 180_000;
            while (authCode[0] == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(500);
            }
            server.stop(0);
            if (authCode[0] == null) {
                errorHandler.accept("Autorisasi Spotify dibatalkan atau timeout.");
                return;
            }
            exchangeCodeForTokens(authCode[0]);
        } catch (Exception e) {
            errorHandler.accept("Autorisasi Spotify gagal: " + e.getClass().getSimpleName());
        }
    }

    private void exchangeCodeForTokens(String code) {
        try {
            String verifier = pkceVerifier.get();
            String form = "grant_type=authorization_code"
                    + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&code_verifier=" + URLEncoder.encode(verifier, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                errorHandler.accept("Spotify token ditolak (HTTP " + response.statusCode() + ").");
                return;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            accessToken.set(json.get("access_token").getAsString());
            if (json.has("refresh_token")) {
                refreshToken.set(json.get("refresh_token").getAsString());
                SecureStore.saveSecret("spotify_refresh_token", refreshToken.get());
            }
            state.status = PlayerState.Status.CONNECTED;
            CraftLive.LOGGER.info("Spotify connected via OAuth PKCE");
            startPolling(5000);
        } catch (Exception e) {
            errorHandler.accept("Spotify token exchange gagal: " + e.getClass().getSimpleName());
        }
    }

    private boolean refreshAccessToken() {
        String refresh = refreshToken.get();
        if (refresh == null) return false;
        try {
            String form = "grant_type=refresh_token"
                    + "&refresh_token=" + URLEncoder.encode(refresh, StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            accessToken.set(json.get("access_token").getAsString());
            if (json.has("refresh_token")) {
                refreshToken.set(json.get("refresh_token").getAsString());
                SecureStore.saveSecret("spotify_refresh_token", refreshToken.get());
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void connect() {
        String saved = SecureStore.getSecret("spotify_refresh_token");
        if (saved != null && !saved.isBlank()) {
            refreshToken.set(saved);
            Thread t = new Thread(() -> {
                if (refreshAccessToken()) {
                    state.status = PlayerState.Status.CONNECTED;
                    startPolling(5000);
                } else {
                    beginAuthorization();
                }
            }, "CraftLive-SpotifyResume");
            t.setDaemon(true);
            t.start();
        } else {
            beginAuthorization();
        }
    }

    @Override
    public void disconnect() {
        stopPolling();
        accessToken.set(null);
        refreshToken.set(null);
        SecureStore.removeSecret("spotify_refresh_token");
        state.status = PlayerState.Status.DISCONNECTED;
        state.current = null;
        state.controlsSupported = true;
    }

    // ---- polling currently-playing ----

    @Override
    public synchronized void startPolling(long intervalMs) {
        if (polling) return;
        polling = true;
        scheduler.scheduleAtFixedRate(this::pollCurrentlyPlaying, 0, Math.max(3, intervalMs / 1000), TimeUnit.SECONDS);
    }

    @Override
    public synchronized void stopPolling() {
        polling = false;
    }

    private void pollCurrentlyPlaying() {
        if (!polling) return;
        try {
            HttpRequest request = apiGet("/me/player/currently-playing");
            if (request == null) return;
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                if (refreshAccessToken()) return;
                state.status = PlayerState.Status.ERROR;
                state.errorMessage = "Spotify sesi berakhir — hubungkan ulang.";
                return;
            }
            if (response.statusCode() == 204 || response.body() == null || response.body().isBlank()) {
                // nothing playing — keep last state, no fabrication
                return;
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("item") || json.get("item").isJsonNull()) return;
            JsonObject item = json.getAsJsonObject("item");

            String title = str(item, "name");
            String artist = joinArtists(item);
            String album = item.has("album") && !item.get("album").isJsonNull()
                    ? str(item.getAsJsonObject("album"), "name") : null;
            long duration = lopt(item, "duration_ms", -1);
            Track track = new Track(title, artist, album, duration, "Spotify");
            if (item.has("external_urls")) {
                track.trackUrl = str(item.getAsJsonObject("external_urls"), "spotify");
            }
            if (item.has("album")) {
                JsonObject albumObj = item.getAsJsonObject("album");
                if (albumObj.has("images") && albumObj.getAsJsonArray("images").size() > 0) {
                    track.artworkUrl = str(albumObj.getAsJsonArray("images").get(0).getAsJsonObject(), "url");
                }
            }
            boolean playing = json.has("is_playing") && json.get("is_playing").getAsBoolean();
            long progress = lopt(json, "progress_ms", 0);

            state.current = track;
            state.progressMs = progress;
            state.lastProgressUpdateMs = System.currentTimeMillis();
            state.status = playing ? PlayerState.Status.PLAYING : PlayerState.Status.PAUSED;
            if (state.errorMessage != null) state.errorMessage = null;
        } catch (Exception e) {
            CraftLive.LOGGER.debug("Spotify poll failed: {}", e.toString());
        }
    }

    // ---- remote control (Premium + active device) ----

    @Override
    public void playPause() {
        if (state.status == PlayerState.Status.PLAYING) {
            apiCallAsync("/me/player/pause", "PUT");
        } else {
            apiCallAsync("/me/player/play", "PUT");
        }
    }

    @Override
    public void next() {
        apiCallAsync("/me/player/next", "POST");
    }

    @Override
    public void previous() {
        apiCallAsync("/me/player/previous", "POST");
    }

    @Override
    public void setVolume(float v) {
        int percent = Math.round(Math.max(0f, Math.min(1f, v)) * 100);
        apiCallAsync("/me/player/volume?volume_percent=" + percent, "PUT");
        state.volume = v;
    }

    @Override
    public boolean supportsSearch() {
        return true;
    }

    @Override
    public void search(String query, Consumer<List<Track>> callback) {
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = apiGet("/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&type=track&limit=12");
                if (request == null) return List.<Track>of();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) return List.<Track>of();
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray items = json.getAsJsonObject("tracks").getAsJsonArray("items");
                List<Track> tracks = new java.util.ArrayList<>();
                for (JsonElement el : items) {
                    JsonObject item = el.getAsJsonObject();
                    Track t = new Track(str(item, "name"), joinArtists(item),
                            item.has("album") ? str(item.getAsJsonObject("album"), "name") : null,
                            lopt(item, "duration_ms", -1), "Spotify");
                    if (item.has("external_urls")) {
                        t.trackUrl = str(item.getAsJsonObject("external_urls"), "spotify");
                    }
                    if (item.has("album") && item.getAsJsonObject("album").has("images")
                            && item.getAsJsonObject("album").getAsJsonArray("images").size() > 0) {
                        t.artworkUrl = str(item.getAsJsonObject("album").getAsJsonArray("images")
                                .get(0).getAsJsonObject(), "url");
                    }
                    tracks.add(t);
                }
                return tracks;
            } catch (Exception e) {
                return List.<Track>of();
            }
        }).thenAccept(callback);
    }

    // ---- helpers ----

    private HttpRequest apiGet(String path) {
        String token = accessToken.get();
        if (token == null) return null;
        return HttpRequest.newBuilder(URI.create(API + path))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(10))
                .GET().build();
    }

    private void apiCallAsync(String path, String method) {
        Thread t = new Thread(() -> {
            try {
                String token = accessToken.get();
                if (token == null) return;
                HttpRequest request = HttpRequest.newBuilder(URI.create(API + path))
                        .header("Authorization", "Bearer " + token)
                        .timeout(Duration.ofSeconds(10))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .build();
                http.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                CraftLive.LOGGER.debug("Spotify control failed: {}", e.toString());
            }
        }, "CraftLive-SpotifyCtl");
        t.setDaemon(true);
        t.start();
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static long lopt(JsonObject o, String key, long def) {
        JsonElement e = o.get(key);
        try {
            return e == null || e.isJsonNull() ? def : e.getAsLong();
        } catch (Exception ex) {
            return def;
        }
    }

    private static String joinArtists(JsonObject item) {
        if (!item.has("artists")) return "";
        JsonArray artists = item.getAsJsonArray("artists");
        StringBuilder sb = new StringBuilder();
        for (JsonElement el : artists) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(str(el.getAsJsonObject(), "name"));
        }
        return sb.toString();
    }

    /** For tests: PKCE challenge correctness. */
    static Map<String, String> pkcePair() {
        String verifier = pkceCodeVerifier();
        Map<String, String> pair = new HashMap<>();
        pair.put("verifier", verifier);
        pair.put("challenge", pkceChallenge(verifier));
        return pair;
    }
}
