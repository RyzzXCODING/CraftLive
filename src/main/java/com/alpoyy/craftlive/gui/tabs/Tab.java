package com.alpoyy.craftlive.gui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A tab inside the CraftLive main GUI.
 */
public interface Tab {
    String id();

    String title();

    /** Called when tab becomes visible (re-init). */
    void onShow();

    void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                double mouseX, double mouseY, float alpha, long epochMs);

    boolean mouseClicked(double mouseX, double mouseY, int button);

    /** Continuous drag (slider handles etc.), screen space. */
    default boolean mouseDragged(double mouseX, double mouseY) {
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double deltaY);

    boolean keyPressed(int key, int modifiers);

    boolean charTyped(char c);

    void mouseReleased();

    /** 20Hz logic tick (countdowns etc.). */
    void tick();
}
