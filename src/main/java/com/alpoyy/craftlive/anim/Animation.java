package com.alpoyy.craftlive.anim;

/**
 * Small FPS-independent animation value: eases toward a target using
 * elapsed delta time (ms). Pure Java, unit-testable.
 */
public class Animation {
    private float value;
    private float target;
    private final float speedPerSecond; // how much of the remaining gap per second

    public Animation(float initial, float speedPerSecond) {
        this.value = initial;
        this.target = initial;
        this.speedPerSecond = Math.max(0.01f, speedPerSecond);
    }

    public void setTarget(float target) {
        this.target = target;
    }

    public void jump(float value) {
        this.value = value;
        this.target = value;
    }

    public void update(long deltaMs) {
        float dt = Math.min(1000f, Math.max(0f, deltaMs)) / 1000f;
        // exponential approach, frame-rate independent
        float t = 1f - (float) Math.exp(-speedPerSecond * dt);
        value += (target - value) * t;
        if (Math.abs(target - value) < 0.001f) value = target;
    }

    public float value() {
        return value;
    }

    public float target() {
        return target;
    }

    public boolean settled() {
        return value == target;
    }

    /** 0..1 progress of a timed one-shot, clamped. */
    public static float progress(long elapsedMs, long durationMs) {
        if (durationMs <= 0) return 1f;
        float p = (float) elapsedMs / durationMs;
        return p < 0 ? 0 : Math.min(1f, p);
    }

    /** Smoothstep easing of 0..1. */
    public static float easeOut(float t) {
        float c = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - c) * (1f - c);
    }

    /** Subtle sine pulse for the LIVE indicator; period ~1.4s. */
    public static float livePulse(long epochMs) {
        double phase = (epochMs % 1400) / 1400.0;
        return 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2);
    }
}
