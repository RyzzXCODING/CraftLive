package com.alpoyy.craftlive.gui.components;

/**
 * Pure scroll math for panel scrolling — no Minecraft classes, unit-tested.
 *
 * Guarantees:
 *  - offset never outside [0, maxScroll]
 *  - smooth approach toward the wheel target (FPS-independent-ish, caller
 *    drives update())
 *  - clamping when content or viewport shrinks
 *  - no scrolling at all when content fits the viewport
 */
public class ScrollModel {
    private float offset = 0;
    private float target = 0;
    private float contentHeight = 0;
    private float viewHeight = 0;

    public void setViewport(float viewHeight) {
        if (this.viewHeight != viewHeight) {
            this.viewHeight = Math.max(0, viewHeight);
            clamp();
        }
    }

    public void setContentHeight(float contentHeight) {
        if (this.contentHeight != contentHeight) {
            this.contentHeight = Math.max(0, contentHeight);
            clamp();
        }
    }

    public void scrollBy(double dy) {
        target += (float) dy;
        clamp();
    }

    public void setTarget(float t) {
        target = t;
        clamp();
    }

    /** Move the visible offset toward the wheel target. Call once per frame. */
    public void update() {
        float diff = target - offset;
        if (Math.abs(diff) < 0.4f) {
            offset = target;
            return;
        }
        offset += diff * 0.35f;
    }

    private void clamp() {
        float max = maxScroll();
        if (target < 0) target = 0;
        if (target > max) target = max;
        if (offset < 0) offset = 0;
        if (offset > max) offset = max;
    }

    public float offset() {
        return offset;
    }

    public float target() {
        return target;
    }

    public float maxScroll() {
        return Math.max(0, contentHeight - viewHeight);
    }

    public boolean scrollable() {
        return maxScroll() > 0.5f;
    }

    public void reset() {
        offset = 0;
        target = 0;
    }
}
