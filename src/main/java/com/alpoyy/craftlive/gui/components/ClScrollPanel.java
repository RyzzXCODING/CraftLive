package com.alpoyy.craftlive.gui.components;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Scissor-clipped scroll region with smooth (targeted) scrolling.
 * The content is drawn by a callback between enable/disable scissor.
 */
public class ClScrollPanel {
    public int x, y, w, h;
    private float scrollOffset = 0;    // px
    private float scrollTarget = 0;
    private float contentHeight = 0;

    public ClScrollPanel(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void setContentHeight(float contentHeight) {
        this.contentHeight = Math.max(0, contentHeight);
        clampScroll();
    }

    public float offset() {
        return scrollOffset;
    }

    public void update() {
        // smooth approach — FPS independent
        scrollOffset += (scrollTarget - scrollOffset) * 0.22f;
        if (Math.abs(scrollTarget - scrollOffset) < 0.5f) scrollOffset = scrollTarget;
    }

    public void scrollBy(double dy) {
        scrollTarget += dy;
        clampScroll();
    }

    private void clampScroll() {
        float max = Math.max(0, contentHeight - h);
        scrollTarget = Math.max(0, Math.min(scrollTarget, max));
    }

    public boolean isScrollbarVisible() {
        return contentHeight > h;
    }

    /** Draw content with clipping + scrollbar. */
    public void render(GuiGraphics g, float alpha, Runnable content) {
        g.enableScissor(x, y, x + w, y + h);
        g.pose().pushMatrix();
        g.pose().translate(0, -scrollOffset);
        content.run();
        g.pose().popMatrix();
        g.disableScissor();

        if (isScrollbarVisible()) {
            float ratio = h / contentHeight;
            int barH = Math.max(24, (int) (h * ratio));
            int barY = y + (int) ((h - barH) * (scrollTarget / Math.max(1, contentHeight - h)));
            int barX = x + w - 4;
            g.fill(barX, y, barX + 3, y + h, 0x20FFFFFF);
            g.fill(barX, barY, barX + 3, barY + barH, 0x66C43A3A);
        }
    }
}
