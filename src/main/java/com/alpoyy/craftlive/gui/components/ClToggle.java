package com.alpoyy.craftlive.gui.components;

import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Toggle switch with animated thumb.
 */
public class ClToggle {
    public final int x, y;
    public final String label;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private float thumbAnim;

    public ClToggle(int x, int y, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        this.x = x;
        this.y = y;
        this.label = label;
        this.getter = getter;
        this.setter = setter;
    }

    public void render(GuiGraphics g, Font font, double mouseX, double mouseY, float alpha) {
        boolean on = getter.getAsBoolean();
        thumbAnim += ((on ? 1f : 0f) - thumbAnim) * 0.25f;

        g.drawString(font, label, x, y + 3, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, alpha), true);

        int tx = x + 150;
        int ty = y;
        int tw = 34;
        int th = 14;
        ClRender.roundedRect(g, tx, ty, tw, th, th / 2,
                CraftLiveTheme.withAlpha(on ? CraftLiveTheme.accent() : CraftLiveTheme.PANEL_2, alpha));
        int thumbX = tx + 2 + Math.round((tw - 4 - 10) * thumbAnim);
        ClRender.roundedRect(g, thumbX, ty + 2, 10, 10, 5,
                CraftLiveTheme.withAlpha(0xFFFFFF, alpha * (on ? 1f : 0.75f)));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (mouseX >= x && mouseX <= x + 190 && mouseY >= y - 2 && mouseY <= y + 16) {
            setter.accept(!getter.getAsBoolean());
            return true;
        }
        return false;
    }

    /** Test hook: current visual state. */
    public boolean isOnForTest() {
        return getter.getAsBoolean();
    }
}
