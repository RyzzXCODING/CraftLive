package com.alpoyy.craftlive.request;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FIFO song-request queue with:
 *  - capacity limit (rejects when full)
 *  - duplicate protection (configurable)
 *  - per-user cooldown (configurable)
 *  - per-user active-request limit (default 1)
 *  - pause/resume, skip, manual removal, clear
 *
 * Pure Java, deterministic, unit-tested. The queue is never randomly
 * reordered: first valid request is first, new requests go to the end.
 */
public class RequestQueue {
    public enum AddResult {
        ADDED("Ditambahkan"),
        FULL("Queue penuh"),
        DUPLICATE("Sudah ada di antrean"),
        COOLDOWN("Cooldown"),
        USER_LIMIT("Maksimal request aktif"),
        PAUSED("Antrean dijeda");

        public final String display;

        AddResult(String display) {
            this.display = display;
        }
    }

    private final ArrayDeque<SongRequest> queue = new ArrayDeque<>();
    private final Set<String> dedupKeys = new HashSet<>();
    private final Map<String, Long> lastRequestAtMs = new HashMap<>();
    private final Map<String, Integer> activePerUser = new HashMap<>();

    private int maxQueue = 30;
    private long userCooldownMs = 30_000;
    private int maxPerUser = 1;
    private boolean duplicateProtection = true;
    private boolean paused = false;

    public RequestQueue() {
    }

    public RequestQueue(int maxQueue, long userCooldownMs, int maxPerUser, boolean duplicateProtection) {
        this.maxQueue = maxQueue;
        this.userCooldownMs = userCooldownMs;
        this.maxPerUser = maxPerUser;
        this.duplicateProtection = duplicateProtection;
    }

    // ---- configuration ----

    public void configure(int maxQueue, long userCooldownMs, int maxPerUser, boolean duplicateProtection) {
        this.maxQueue = Math.max(1, maxQueue);
        this.userCooldownMs = Math.max(0, userCooldownMs);
        this.maxPerUser = Math.max(1, maxPerUser);
        this.duplicateProtection = duplicateProtection;
    }

    public int maxQueue() {
        return maxQueue;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    // ---- queries ----

    public synchronized int size() {
        return queue.size();
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    public synchronized List<SongRequest> snapshot() {
        return new ArrayList<>(queue);
    }

    public synchronized SongRequest peek() {
        return queue.peekFirst();
    }

    public synchronized SongRequest peekNext() {
        Iterator<SongRequest> it = queue.iterator();
        if (it.hasNext()) it.next();
        return it.hasNext() ? it.next() : null;
    }

    /** Milliseconds remaining on a user's cooldown, 0 if ready. */
    public synchronized long cooldownRemainingMs(String user, long nowMs) {
        Long last = lastRequestAtMs.get(key(user));
        if (last == null) return 0;
        long elapsed = nowMs - last;
        return elapsed >= userCooldownMs ? 0 : userCooldownMs - elapsed;
    }

    /**
     * Add a request. Returns the exact reason when rejected so the UI can
     * show "Queue penuh (30/30), coba lagi nanti." etc.
     */
    public synchronized AddResult add(SongRequest request, long nowMs) {
        if (paused) return AddResult.PAUSED;
        String userKey = key(request.requester);

        if (queue.size() >= maxQueue) {
            return AddResult.FULL;
        }
        if (duplicateProtection && dedupKeys.contains(request.dedupKey())) {
            return AddResult.DUPLICATE;
        }
        long remaining = cooldownRemainingMs(request.requester, nowMs);
        if (remaining > 0) {
            return AddResult.COOLDOWN;
        }
        int active = activePerUser.getOrDefault(userKey, 0);
        if (active >= maxPerUser) {
            return AddResult.USER_LIMIT;
        }

        queue.addLast(request);
        dedupKeys.add(request.dedupKey());
        lastRequestAtMs.put(userKey, nowMs);
        activePerUser.merge(userKey, 1, Integer::sum);
        return AddResult.ADDED;
    }

    /** Pop the head request (marking it PLAYING is the manager's job). */
    public synchronized SongRequest poll() {
        SongRequest head = queue.pollFirst();
        if (head != null) {
            dedupKeys.remove(head.dedupKey());
            activePerUser.merge(key(head.requester), -1, Integer::sum);
            if (activePerUser.get(key(head.requester)) <= 0) {
                activePerUser.remove(key(head.requester));
            }
        }
        return head;
    }

    /** Peek head without removing. */
    public synchronized SongRequest current() {
        return queue.peekFirst();
    }

    /** Skip the head request (returns it, marks nothing). */
    public synchronized SongRequest skipHead() {
        return poll();
    }

    /** Remove a specific request by id. */
    public synchronized boolean remove(long id) {
        Iterator<SongRequest> it = queue.iterator();
        while (it.hasNext()) {
            SongRequest r = it.next();
            if (r.id == id) {
                it.remove();
                dedupKeys.remove(r.dedupKey());
                activePerUser.merge(key(r.requester), -1, Integer::sum);
                if (activePerUser.get(key(r.requester)) <= 0) {
                    activePerUser.remove(key(r.requester));
                }
                return true;
            }
        }
        return false;
    }

    /** Position of a request (1-based), or -1 when absent. */
    public synchronized int position(long id) {
        int i = 1;
        for (SongRequest r : queue) {
            if (r.id == id) return i;
            i++;
        }
        return -1;
    }

    public synchronized void clear() {
        queue.clear();
        dedupKeys.clear();
        activePerUser.clear();
    }

    /** Clear cooldown bookkeeping (e.g. after a long disconnection). */
    public synchronized void resetCooldowns() {
        lastRequestAtMs.clear();
    }

    private static String key(String user) {
        return user == null ? "" : user.trim().toLowerCase();
    }
}
