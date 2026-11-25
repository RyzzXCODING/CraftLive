package com.alpoyy.craftlive.gui.components;

import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/**
 * Horizontal slider with animated handle.
 */
public class ClSlider {
    public final int x, y, w;
    public final String label;
    private final DoubleSupplier getter;
    private final Consumer<Float> setter;
    private boolean dragging = false;
    public String format = "%.2f";
    /** Optional custom label renderer (receives the 0..1 slider value).
     * When set, takes precedence over {@link #format} — and failures in
     * either path can never crash the GUI (see valueLabel). */
    public java.util.function.DoubleFunction<String> labelFormatter = null;

    public ClSlider(int x, int y, int w, String label, DoubleSupplier getter, Consumer<Float> setter) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.label = label;
        this.getter = getter;
        this.setter = setter;
    }

    public void render(GuiGraphics g, Font font, double mouseX, double mouseY, float alpha) {
        g.drawString(font, label, x, y, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, alpha), true);
        float value = (float) getter.getAsDouble();

        int trackY = y + 12;
        ClRender.roundedRect(g, x, trackY, w, 4, 2,
                CraftLiveTheme.withAlpha(CraftLiveTheme.PANEL_2, alpha));
        int handleX = x + (int) (w * Math.max(0f, Math.min(1f, value)));
        ClRender.roundedRect(g, x, trackY, handleX - x, 4, 2,
                CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT, alpha));
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= trackY - 4 && mouseY <= trackY + 10;
        ClRender.roundedRect(g, handleX - 4, trackY - 3, 8, 10, 4,
                CraftLiveTheme.withAlpha(hovered || dragging ? CraftLiveTheme.ACCENT_HOVER : 0xFFFFFF, alpha));

        String valueText = valueLabel(value);
        g.drawString(font, valueText, x + w + 8, y + 6, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, alpha), true);
    }

    /** Never throws: a bad format string degrades to a plain number instead
     * of crashing the screen (previously "%+d" with a float value crashed). */
    private String valueLabel(float value) {
        if (labelFormatter != null) {
            try {
                return labelFormatter.apply(value);
            } catch (Exception ignored) {
            }
        }
        try {
            return String.format(format, value);
        } catch (Exception e) {
            return String.format("%.2f", value);
        }
    }

    /** Unit test hook: label formatting must never throw. */
    public String labelForTest(float value) {
        return valueLabel(value);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int trackY = y + 12;
        if (mouseX >= x - 4 && mouseX <= x + w + 4 && mouseY >= trackY - 4 && mouseY <= trackY + 10) {
            dragging = true;
            apply(mouseX);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX) {
        if (dragging) {
            apply(mouseX);
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        dragging = false;
    }

    private void apply(double mouseX) {
        float v = (float) ((mouseX - x) / (double) w);
        v = Math.max(0f, Math.min(1f, v));
        setter.accept(v);
    }
}
