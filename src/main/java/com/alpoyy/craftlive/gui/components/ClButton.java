package com.alpoyy.craftlive.gui.components;

import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * Theme-styled button with press animation ("bounce-pop").
 */
public class ClButton {
    public enum Style { PRIMARY, SECONDARY, GHOST, DANGER, SUCCESS }

    public int x, y, w, h;
    public final String label;
    public final Style style;
    private final Consumer<ClButton> onClick;
    private float pressAnim = 0f;
    public boolean enabled = true;
    public String tooltip;

    public ClButton(int x, int y, int w, int h, String label, Style style, Consumer<ClButton> onClick) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.label = label;
        this.style = style;
        this.onClick = onClick;
    }

    /** Fluent tweak helper, e.g. .with(b -> b.tooltip = "...") */
    public ClButton with(java.util.function.Consumer<ClButton> tweak) {
        tweak.accept(this);
        return this;
    }

    public void render(GuiGraphics g, Font font, double mouseX, double mouseY, float alpha, boolean animations) {
        boolean hovered = enabled && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        if (pressAnim > 0) pressAnim = Math.max(0, pressAnim - 0.12f);
        float pop = animations ? pressAnim * 2f : 0f;

        int base = switch (style) {
            case PRIMARY -> CraftLiveTheme.accent();
            case DANGER -> CraftLiveTheme.ERROR;
            case SUCCESS -> CraftLiveTheme.LIVE_GREEN;
            case SECONDARY -> CraftLiveTheme.PANEL_2;
            case GHOST -> CraftLiveTheme.PANEL;
        };
        int color = enabled ? (hovered ? CraftLiveTheme.accentHover() : base) : CraftLiveTheme.BORDER;
        float inset = pop > 0 ? pop : 0;
        ClRender.roundedRectBordered(g,
                x + (int) inset, y + (int) inset, w - 2 * (int) inset, h - 2 * (int) inset, 5,
                CraftLiveTheme.withAlpha(color, alpha * 0.95f),
                CraftLiveTheme.withAlpha(hovered ? CraftLiveTheme.accentHover() : CraftLiveTheme.BORDER, alpha));

        int textW = font.width(label);
        int textY = y + (h - 8) / 2;
        g.drawString(font, label, x + (w - textW) / 2, textY,
                CraftLiveTheme.withAlpha(enabled ? CraftLiveTheme.TEXT : CraftLiveTheme.TEXT_DIM, alpha), true);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || button != 0) return false;
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            pressAnim = 1f;
            if (onClick != null) onClick.accept(this);
            return true;
        }
        return false;
    }
}
