package com.alpoyy.craftlive.gui.components;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Scissor-clipped scroll region with smooth scrolling and a visual
 * scrollbar. Scroll math lives in {@link ScrollModel} (pure, tested).
 */
public class ClScrollPanel {
    public int x, y, w, h;
    public final ScrollModel model = new ScrollModel();
    private boolean scrollbarDragging = false;

    public ClScrollPanel(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void setContentHeight(float contentHeight) {
        model.setContentHeight(contentHeight);
    }

    public float offset() {
        return model.offset();
    }

    public void scrollBy(double dy) {
        model.scrollBy(dy);
    }

    public void reset() {
        model.reset();
    }

    public boolean isScrollbarVisible() {
        return model.scrollable();
    }

    /** True when the cursor is inside this panel's bounds. */
    public boolean isOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    // ---- scrollbar dragging ----

    /** Begin dragging the scrollbar thumb; true when the press hit it. */
    public boolean startScrollbarDrag(double mouseX, double mouseY) {
        if (!isScrollbarVisible()) return false;
        if (mouseX >= x + w - 7 && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            scrollbarDragging = true;
            applyThumbDrag(mouseY);
            return true;
        }
        return false;
    }

    public boolean dragScrollbar(double mouseY) {
        if (!scrollbarDragging) return false;
        applyThumbDrag(mouseY);
        return true;
    }

    public void stopScrollbarDrag() {
        scrollbarDragging = false;
    }

    public boolean isScrollbarDragging() {
        return scrollbarDragging;
    }

    private void applyThumbDrag(double mouseY) {
        float contentTotal = model.maxScroll() + h;
        int barH = Math.max(24, (int) (h * (h / contentTotal)));
        float track = Math.max(1f, h - barH);
        float frac = (float) ((mouseY - y) - barH / 2f) / track;
        frac = Math.max(0f, Math.min(1f, frac));
        model.setTarget(frac * model.maxScroll());
    }

    /** Draw content with clipping + scrollbar. */
    public void render(GuiGraphics g, float alpha, Runnable content) {
        model.setViewport(h);
        model.update(); // advance smooth scroll exactly once per frame

        g.enableScissor(x, y, x + w, y + h);
        g.pose().pushMatrix();
        g.pose().translate(0, -model.offset());
        content.run();
        g.pose().popMatrix();
        g.disableScissor();

        if (isScrollbarVisible()) {
            // thumb size ∝ viewport/content, position ∝ offset/maxScroll
            float contentTotal = model.maxScroll() + h;
            int barH = Math.max(24, (int) (h * (h / contentTotal)));
            float frac = model.offset() / model.maxScroll();
            int barY = y + (int) ((h - barH) * frac);
            int barX = x + w - 4;
            g.fill(barX, y, barX + 3, y + h, 0x20FFFFFF);
            int accent = com.alpoyy.craftlive.theme.CraftLiveTheme.withAlpha(
                    com.alpoyy.craftlive.theme.CraftLiveTheme.accent(), 0x66);
            g.fill(barX, barY, barX + 3, barY + barH, accent);
        }
    }
}
