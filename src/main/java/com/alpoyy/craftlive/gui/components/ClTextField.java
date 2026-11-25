package com.alpoyy.craftlive.gui.components;

import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Text field with masked (password) mode, cursor blink, clipboard support
 * (Ctrl+V paste, Ctrl+A select-all), and a max length.
 * Respects focus — only one field focused at a time (managed by the screen).
 */
public class ClTextField {
    public int x, y, w, h;
    public final String hint;
    private final Consumer<String> onChange;
    private String text = "";
    private boolean focused = false;
    private boolean masked = false;
    private int maxLength = 256;
    private long lastClickMs = 0;

    public ClTextField(int x, int y, int w, int h, String hint, Consumer<String> onChange) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.hint = hint;
        this.onChange = onChange;
    }

    public void setMasked(boolean masked) {
        this.masked = masked;
    }

    public void setMaxLength(int max) {
        this.maxLength = max;
    }

    public String text() {
        return text;
    }

    public void setText(String t) {
        this.text = t == null ? "" : t;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean f) {
        this.focused = f;
    }

    /** Displayed text: bullets when masked. */
    private String display() {
        return masked ? "•".repeat(Math.min(text.length(), 32)) : text;
    }

    public void render(GuiGraphics g, Font font, double mouseX, double mouseY, float alpha, long epochMs) {
        int borderColor = focused ? CraftLiveTheme.ACCENT : CraftLiveTheme.BORDER;
        ClRender.roundedRectBordered(g, x, y, w, h, 5,
                CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL, alpha * 0.9f),
                CraftLiveTheme.withAlpha(borderColor, alpha));

        String shown = display();
        boolean empty = shown.isEmpty();
        String toDraw = empty ? hint : shown;
        int color = empty ? CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, alpha * 0.6f)
                : CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, alpha);
        // clip inside field
        String clipped = ClRender.clip(font, toDraw, w - 12);
        int cursorW = font.width(clipped);
        g.drawString(font, clipped, x + 6, y + (h - 8) / 2, color, false);

        // cursor blink when focused
        if (focused && (epochMs / 530) % 2 == 0 && !empty) {
            g.fill(x + 7 + Math.min(cursorW, w - 14), y + 3, x + 8 + Math.min(cursorW, w - 14), y + h - 3,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, alpha));
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        boolean hit = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        focused = hit;
        return hit;
    }

    public boolean keyPressed(int key, int modifiers) {
        if (!focused) return false;
        if (key == GLFW.GLFW_KEY_BACKSPACE && !text.isEmpty()) {
            text = text.substring(0, text.length() - 1);
            notifyChange();
            return true;
        }
        if (key == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            var handler = net.minecraft.client.Minecraft.getInstance().keyboardHandler;
            String clip = handler == null ? "" : handler.getClipboard();
            if (clip != null) insert(clip);
            return true;
        }
        return false;
    }

    private void insert(String s) {
        if (s == null) return;
        if (text.length() + s.length() > maxLength) {
            s = s.substring(0, Math.max(0, maxLength - text.length()));
        }
        text += s;
        notifyChange();
    }

    public boolean charTyped(char c) {
        if (!focused) return false;
        if (c >= 32 && c != 127) {
            insert(String.valueOf(c));
            return true;
        }
        return false;
    }

    private void notifyChange() {
        if (onChange != null) onChange.accept(text);
    }
}
