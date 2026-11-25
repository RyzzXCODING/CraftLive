package com.alpoyy.craftlive.request;

import com.alpoyy.craftlive.CraftLive;
import com.alpoyy.craftlive.config.CraftLiveConfig;
import com.alpoyy.craftlive.live.LiveEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Coordinates the whole Song Request pipeline:
 * chat event → parse → approve → queue → (optional) delay countdown → mark playing.
 *
 * Everything runs off the Minecraft main thread except UI notification
 * delivery, which consumers handle themselves.
 *
 * The clock is injectable ({@link LongSupplier}) so countdowns and
 * cooldowns are deterministic in unit tests.
 */
public class RequestManager {
    private final CraftLiveConfig config;
    private final RequestParser parser;
    private final RequestQueue queue = new RequestQueue();
    private final LongSupplier clock;

    private final List<Consumer<SongRequest>> requestAddedListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<String>> messageListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> queueChangedListeners = new CopyOnWriteArrayList<>();

    // playback progress of the current request (managed here so the HUD
    // widget can show state without any platform integration)
    private SongRequest currentRequest;
    private long currentStartedAtMs = 0;

    public RequestManager(CraftLiveConfig config) {
        this(config, System::currentTimeMillis);
    }

    public RequestManager(CraftLiveConfig config, LongSupplier clock) {
        this.config = config;
        this.clock = clock;
        this.parser = new RequestParser(config.requestCommand);
        applyConfig();
    }

    public void applyConfig() {
        queue.configure(config.requestMaxQueue, config.requestUserCooldownSeconds * 1000L,
                config.requestMaxPerUser, config.requestDuplicateProtection);
    }

    public RequestQueue queue() {
        return queue;
    }

    public SongRequest currentRequest() {
        return currentRequest;
    }

    public long currentElapsedMs() {
        return currentRequest == null ? 0 : clock.getAsLong() - currentStartedAtMs;
    }

    // ---- listeners ----

    public void onRequestAdded(Consumer<SongRequest> l) {
        requestAddedListeners.add(l);
    }

    public void onMessage(Consumer<String> l) {
        messageListeners.add(l);
    }

    public void onQueueChanged(Runnable l) {
        queueChangedListeners.add(l);
    }

    private void notifyAdded(SongRequest r) {
        for (Consumer<SongRequest> l : requestAddedListeners) {
            try {
                l.accept(r);
            } catch (Exception e) {
                CraftLive.LOGGER.error("requestAdded listener failed", e);
            }
        }
    }

    private void notifyMessage(String message) {
        for (Consumer<String> l : messageListeners) {
            try {
                l.accept(message);
            } catch (Exception e) {
                CraftLive.LOGGER.error("message listener failed", e);
            }
        }
    }

    private void notifyChanged() {
        for (Runnable l : queueChangedListeners) {
            try {
                l.run();
            } catch (Exception e) {
                CraftLive.LOGGER.error("queueChanged listener failed", e);
            }
        }
    }

    // ---- pipeline ----

    /** Feed a live event in (any type; only CHAT with the command matters). */
    public void onLiveEvent(LiveEvent event) {
        if (event == null || event.type != LiveEvent.Type.CHAT) return;
        RequestParser.Parsed parsed = parser.parse(event, clock.getAsLong());
        if (parsed == null) return;                       // not a request command
        if (!config.requestsEnabled) return;
        if (!parsed.valid) {
            if (parsed.invalidReason != null && !parsed.invalidReason.isBlank()) {
                notifyMessage("@" + parsed.requester + ": " + parsed.invalidReason);
            }
            return;
        }

        SongRequest request = new SongRequest(parsed.requester, parsed.raw, parsed.artist,
                parsed.track, parsed.atMs);

        if (config.requestAutoApprove) {
            addToQueue(request);
        } else {
            request.status = SongRequest.Status.PENDING;
            notifyMessage("@" + request.requester + " meminta: " + request.title + " (menunggu persetujuan)");
            pendingForApproval.add(request);
            notifyChanged();
        }
    }

    private final List<SongRequest> pendingForApproval = new CopyOnWriteArrayList<>();

    public List<SongRequest> pendingApproval() {
        return List.copyOf(pendingForApproval);
    }

    public void approve(SongRequest request) {
        if (pendingForApproval.remove(request)) {
            addToQueue(request);
        }
    }

    public void reject(SongRequest request) {
        if (pendingForApproval.remove(request)) {
            request.status = SongRequest.Status.REJECTED;
            request.rejectReason = "Ditolak host";
            notifyMessage("@" + request.requester + ": request ditolak.");
            notifyChanged();
        }
    }

    private void addToQueue(SongRequest request) {
        long now = clock.getAsLong();

        // duplicate guard also covers the currently playing request
        if (config.requestDuplicateProtection && currentRequest != null
                && currentRequest.dedupKey().equals(request.dedupKey())) {
            notifyMessage("@" + request.requester + ": lagu sudah ada di antrean.");
            return;
        }

        RequestQueue.AddResult result = queue.add(request, now);
        switch (result) {
            case ADDED -> {
                request.status = SongRequest.Status.QUEUED;
                int pos = queue.position(request.id)
                        + (currentRequest != null && currentRequest.status == SongRequest.Status.PLAYING ? 1 : 0);
                notifyAdded(request);
                notifyMessage("@" + request.requester + " request \"" + request.title
                        + "\" diterima. Posisi antrean: " + pos + "/" + config.requestMaxQueue);
                notifyChanged();
                if (currentRequest == null) {
                    startNextIfIdle();
                }
            }
            case FULL -> notifyMessage(config.requestQueueFullMessage
                    .replace("(30/30)", "(" + config.requestMaxQueue + "/" + config.requestMaxQueue + ")"));
            case DUPLICATE -> notifyMessage("@" + request.requester
                    + ": lagu sudah ada di antrean.");
            case COOLDOWN -> notifyMessage("@" + request.requester + ": tunggu "
                    + config.requestUserCooldownSeconds + " detik antar request.");
            case USER_LIMIT -> notifyMessage("@" + request.requester
                    + ": maksimal " + config.requestMaxPerUser + " request aktif.");
            case PAUSED -> notifyMessage("@" + request.requester + ": antrean sedang dijeda.");
        }
    }

    /** When true (real audio still playing), the pipeline waits instead of
     * advancing after the simulated delay. Default false (simulation mode). */
    public void setPlaybackGate(java.util.function.BooleanSupplier gate) {
        this.playbackGate = gate == null ? () -> false : gate;
    }

    private volatile java.util.function.BooleanSupplier playbackGate = () -> false;

    /**
     * Advance the pipeline. Call this every client tick (or from tests with a
     * fake clock): it enforces the configured delay between marking a request
     * as playing and starting the next one. With real audio playback active
     * (playback gate), the current request is held until the track ends or is
     * skipped — the simulated delay never cuts off a real song.
     */
    public void tick() {
        if (currentRequest != null
                && currentRequest.status == SongRequest.Status.PLAYING) {
            if (playbackGate.getAsBoolean()) {
                return; // real audio playing — hold
            }
            long elapsed = clock.getAsLong() - currentStartedAtMs;
            if (elapsed >= config.requestDelayMs) {
                finishCurrent();
                startNextIfIdle();
            }
        } else {
            startNextIfIdle();
        }
    }

    private void startNextIfIdle() {
        if (currentRequest != null) return;
        if (!config.requestsEnabled) return;
        SongRequest next = queue.poll();
        if (next == null) return;
        next.status = SongRequest.Status.PLAYING;
        currentRequest = next;
        currentStartedAtMs = clock.getAsLong();
        notifyMessage("Sekarang: \"" + next.title + "\" oleh @" + next.requester);
        notifyChanged();
    }

    private void finishCurrent() {
        if (currentRequest != null) {
            currentRequest.status = SongRequest.Status.DONE;
            currentRequest = null;
        }
    }

    // ---- manual controls ----

    public void skipCurrent() {
        if (currentRequest != null) {
            currentRequest.status = SongRequest.Status.CANCELLED;
            notifyMessage("Request \"" + currentRequest.title + "\" dilewati.");
            currentRequest = null;
            startNextIfIdle();
            notifyChanged();
        }
    }

    public boolean removeById(long id) {
        boolean removed = queue.remove(id);
        if (removed) notifyChanged();
        return removed;
    }

    public void clearQueue() {
        queue.clear();
        notifyMessage("Antrean dikosongkan.");
        notifyChanged();
    }

    public void setPaused(boolean paused) {
        queue.setPaused(paused);
        notifyMessage(paused ? "Antrean dijeda." : "Antrean dilanjutkan.");
        notifyChanged();
    }

    /**
     * Called when the live connection drops: the queue survives by design
     * (spec: "Preserve queue during connection interruptions").
     */
    public void onConnectionLost() {
        // intentionally keep queue state
    }

    /** Millis until the current request advances (for countdown UI). */
    public long countdownRemainingMs() {
        if (currentRequest == null || currentRequest.status != SongRequest.Status.PLAYING) return 0;
        long elapsed = clock.getAsLong() - currentStartedAtMs;
        return Math.max(0, config.requestDelayMs - elapsed);
    }

    public List<SongRequest> queueSnapshot() {
        return queue.snapshot();
    }
}
