package com.alpoyy.craftlive.mock;

import com.alpoyy.craftlive.live.ConnectionState;
import com.alpoyy.craftlive.live.LiveEvent;
import com.alpoyy.craftlive.live.LiveProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Mock Data Mode provider: generates realistic TikTok LIVE events so every
 * CraftLive feature can be exercised without an EulerStream API key or a
 * real connection. Mock data is ALWAYS clearly labeled in the UI and must
 * never be mistaken for real live data.
 */
public class MockLiveProvider implements LiveProvider {
    private static final String[] USERS = {
            "rizky_gamer", "siti_nurhaliza_fan", "budi01", "anak_minecraft",
            "streamer_indo", "kopi_susu", "nayla_puspita", "galih_pratama"
    };
    private static final String[] DISPLAYS = {
            "Rizky", "Siti❤", "Budi", "Anak MC", "StreamerIndo", "KopiSusu", "Nayla", "Galih"
    };
    private static final String[] MESSAGES = {
            "Halo semua!", "!req Nadir - Sial", "ini lagunya enak banget",
            "Game apa ini kak?", "!req Bernadya - Untungnya", "keren streamnya",
            "!req Hindia - Rumah ke Rumah", "siapa yang udah follow kakak?",
            "Salam dari Surabaya", "!req Payung Teduh - Rahasia", "mantap kak",
            "waduh keren banget", "!req Tulus - Hati-Hati di Jalan", "request dong kak"
    };
    private static final String[] GIFTS = {"Rose", "TikTok", "Finger Heart", "GG", "Perfume", "Corgi"};

    private final Random random = new Random();
    private ScheduledExecutorService executor;
    private Consumer<LiveEvent> listener = e -> {};
    private ConnectionState state = ConnectionState.NOT_CONFIGURED;
    private String lastError = null;
    private int tick = 0;

    @Override
    public String name() {
        return "Mock Mode";
    }

    @Override
    public ConnectionState state() {
        return state;
    }

    @Override
    public boolean requiresApiKey() {
        return false;
    }

    @Override
    public String lastError() {
        return lastError;
    }

    @Override
    public synchronized void connect(String username, String apiKey, Consumer<LiveEvent> out) {
        disconnect();
        this.listener = out;
        this.state = ConnectionState.CONNECTING;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CraftLive-MockLive");
            t.setDaemon(true);
            return t;
        });
        // simulate a short connect handshake
        executor.schedule(() -> {
            state = ConnectionState.CONNECTED;
            listener.accept(LiveEvent.social(LiveEvent.Type.CONNECTED, null, null));
            listener.accept(LiveEvent.roomInfo(1200 + random.nextInt(400), "Mock LIVE — CraftLive Test"));
        }, 900, TimeUnit.MILLISECONDS);
        // then emit a steady stream of events
        executor.scheduleAtFixedRate(this::emitRandomEvent, 2, 2, TimeUnit.SECONDS);
    }

    private void emitRandomEvent() {
        if (state != ConnectionState.CONNECTED) return;
        tick++;
        int pick = random.nextInt(100);
        if (pick < 45) {
            int i = random.nextInt(USERS.length);
            listener.accept(LiveEvent.chat(USERS[i], DISPLAYS[i], MESSAGES[random.nextInt(MESSAGES.length)]));
        } else if (pick < 60) {
            listener.accept(LiveEvent.like(USERS[random.nextInt(USERS.length)], null,
                    1 + random.nextInt(5), 5000 + tick * 7));
        } else if (pick < 68) {
            listener.accept(LiveEvent.gift(USERS[random.nextInt(USERS.length)], null,
                    GIFTS[random.nextInt(GIFTS.length)], 1 + random.nextInt(3), 1 + random.nextInt(99)));
        } else if (pick < 74) {
            listener.accept(LiveEvent.social(LiveEvent.Type.FOLLOW, USERS[random.nextInt(USERS.length)], null));
        } else if (pick < 80) {
            listener.accept(LiveEvent.social(LiveEvent.Type.JOIN, USERS[random.nextInt(USERS.length)], null));
        } else if (pick < 83) {
            listener.accept(LiveEvent.social(LiveEvent.Type.SHARE, USERS[random.nextInt(USERS.length)], null));
        } else {
            int v = 1200 + random.nextInt(600);
            listener.accept(LiveEvent.roomInfo(v, "Mock LIVE — CraftLive Test"));
        }
    }

    @Override
    public synchronized void disconnect() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
            state = ConnectionState.DISCONNECTED;
        }
    }

    /** Deterministic scripted scenario used by automated tests. */
    public static List<LiveEvent> scriptedScenario() {
        List<LiveEvent> events = new ArrayList<>();
        events.add(LiveEvent.roomInfo(1500, "Test Room"));
        events.add(LiveEvent.chat("budi01", "Budi", "!req Nadir - Sial"));
        events.add(LiveEvent.like("budi01", "Budi", 3, 42));
        events.add(LiveEvent.gift("siti_fan", "Siti", "Rose", 2, 1));
        events.add(LiveEvent.social(LiveEvent.Type.FOLLOW, "galih", "Galih"));
        events.add(LiveEvent.social(LiveEvent.Type.JOIN, "nayla", "Nayla"));
        return events;
    }
}
