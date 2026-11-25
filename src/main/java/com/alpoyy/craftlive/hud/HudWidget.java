package com.alpoyy.craftlive.hud;

import com.alpoyy.craftlive.anim.Animation;
import com.alpoyy.craftlive.config.CraftLiveConfig;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

/**
 * Base class for floating HUD widgets.
 * Supports: position, scale, opacity, visibility, drag & drop,
 * screen-boundary protection, pop-in animation, persistence.
 * Pure rendering only — no state mutation here.
 */
public abstract class HudWidget {
    public final String id;
    public final String title;

    protected float x, y;              // top-left, GUI-scaled pixels
    protected int width, height;       // base size at scale 1
    protected float scale = 1f;
    protected float opacity = 1f;
    protected boolean visible = true;

    // editor/drag state
    protected boolean dragging = false;
    protected float dragOffX, dragOffY;
    protected boolean selected = false;

    // pop-in animation
    private final Animation popIn = new Animation(0f, 9f);
    private boolean everShown = false;

    protected HudWidget(String id, String title, float x, float y, int width, int height) {
        this.id = id;
        this.title = title;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // ---- layout ----

    public int baseWidth() {
        return width;
    }

    public int baseHeight() {
        return height;
    }

    public float effectiveScale() {
        return scale;
    }

    public float renderWidth() {
        return width * scale;
    }

    public float renderHeight() {
        return height * scale;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        clampToScreen();
    }

    public void setScale(float s) {
        this.scale = Math.max(0.5f, Math.min(2f, s));
    }

    public void setOpacity(float o) {
        this.opacity = Math.max(0.1f, Math.min(1f, o));
    }

    public float opacity() {
        return opacity;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean v) {
        this.visible = v;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean s) {
        this.selected = s;
    }

    /** Keep inside the current GUI-scaled window. */
    public void clampToScreen() {
        var mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float maxW = Math.min(renderWidth(), sw - 8);
        x = Math.max(4, Math.min(x, sw - maxW - 4));
        y = Math.max(4, Math.min(y, sh - Math.min(renderHeight(), sh - 8) - 4));
    }

    // ---- drag (editor) ----

    public void startDrag(double mouseX, double mouseY) {
        dragging = true;
        dragOffX = (float) mouseX - x;
        dragOffY = (float) mouseY - y;
    }

    public void dragTo(double mouseX, double mouseY, boolean snap, int gridSize) {
        if (!dragging) return;
        float nx = (float) (mouseX - dragOffX);
        float ny = (float) (mouseY - dragOffY);
        if (snap && gridSize > 1) {
            nx = Math.round(nx / gridSize) * gridSize;
            ny = Math.round(ny / gridSize) * gridSize;
        }
        this.x = nx;
        this.y = ny;
        clampToScreen();
    }

    public void stopDrag() {
        dragging = false;
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + renderWidth()
                && mouseY >= y && mouseY <= y + renderHeight();
    }

    // ---- persistence ----

    public void saveTo(CraftLiveConfig config) {
        CraftLiveConfig.WidgetLayout l = config.widgetLayouts.computeIfAbsent(id, k -> new CraftLiveConfig.WidgetLayout());
        l.x = x;
        l.y = y;
        l.scale = scale;
        l.opacity = opacity;
        l.visible = visible;
    }

    public void loadFrom(CraftLiveConfig config) {
        CraftLiveConfig.WidgetLayout l = config.widgetLayouts.get(id);
        if (l != null) {
            this.x = l.x;
            this.y = l.y;
            setScale(l.scale);
            setOpacity(l.opacity);
            this.visible = l.visible;
        }
        clampToScreen();
    }

    public void resetLayout(float x, float y) {
        this.x = x;
        this.y = y;
        setScale(1f);
        setOpacity(1f);
        this.visible = true;
        clampToScreen();
    }

    // ---- rendering ----

    /** Called each frame when visible. Handles scale/opacity/pop-in; subclass draws content. */
    public final void render(GuiGraphics g, float globalScale, float globalOpacity, boolean editorMode, long epochMs) {
        if (!visible) return;
        float alpha = opacity * globalOpacity;
        if (alpha <= 0.02f) return;

        // pop-in animation
        if (!everShown) {
            everShown = true;
            popIn.jump(0f);
            popIn.setTarget(1f);
        }
        popIn.update(16);
        float pop = popIn.value();
        float eased = 1f - (1f - pop) * (1f - pop);

        float s = scale * globalScale;
        var pose = g.pose();
        pose.pushMatrix();
        float cx = x + renderWidth() * globalScale / 2f;
        float cy = y + renderHeight() * globalScale / 2f;
        pose.translate(x, y);
        pose.scale(s * (0.92f + 0.08f * eased), s * (0.92f + 0.08f * eased));
        try {
            drawContent(g, (int) (width), (int) (height), alpha, editorMode, epochMs);
        } catch (Exception e) {
            // never crash the HUD from a widget
            com.alpoyy.craftlive.CraftLive.LOGGER.error("Widget {} render error: {}", id, e.toString());
        }
        pose.popMatrix();

        if (editorMode && selected) {
            var mc = Minecraft.getInstance();
            int sw = (int) (renderWidth() * globalScale) + 4;
            int sh = (int) (renderHeight() * globalScale) + 4;
            g.renderOutline((int) x - 2, (int) y - 2, sw, sh, CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT_HOVER, 0.9f));
        }
    }

    /** Subclass draws at (0,0) in its local coordinate space of (width, height). */
    protected abstract void drawContent(GuiGraphics g, int w, int h, float alpha, boolean editorMode, long epochMs);

    // ---- shared drawing helpers for subclasses ----

    protected static void drawWidgetBackground(GuiGraphics g, int w, int h, float alpha, boolean simplify) {
        int bg = simplify ? CraftLiveTheme.withAlpha(CraftLiveTheme.BG, alpha * 0.88f)
                : CraftLiveTheme.withAlpha(CraftLiveTheme.BG, alpha * 0.82f);
        ClRender.roundedRectBordered(g, 0, 0, w, h, 6, bg,
                CraftLiveTheme.withAlpha(CraftLiveTheme.BORDER, alpha * 0.9f));
        // accent top edge
        g.fill(6, 0, w - 6, 1, CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT, alpha * 0.7f));
    }

    protected static Font font() {
        return Minecraft.getInstance().font;
    }
}
