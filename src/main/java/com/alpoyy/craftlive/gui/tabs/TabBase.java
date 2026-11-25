package com.alpoyy.craftlive.gui.tabs;

import com.alpoyy.craftlive.gui.components.ClButton;
import com.alpoyy.craftlive.gui.components.ClScrollPanel;
import com.alpoyy.craftlive.gui.components.ClSlider;
import com.alpoyy.craftlive.gui.components.ClTextField;
import com.alpoyy.craftlive.gui.components.ClToggle;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared tab plumbing: scroll panel, control lists, section headers.
 */
public abstract class TabBase implements Tab {
    public final List<ClButton> buttons = new ArrayList<>();
    public final List<ClToggle> toggles = new ArrayList<>();
    public final List<ClSlider> sliders = new ArrayList<>();
    public final List<ClTextField> fields = new ArrayList<>();
    protected ClScrollPanel scroll;
    protected int contentHeight;

    /** Rebuild controls; called on show and on layout changes. */
    protected abstract void build();

    @Override
    public void onShow() {
        buttons.clear();
        toggles.clear();
        sliders.clear();
        fields.clear();
        contentHeight = 0;
        build();
    }

    protected void layout(int x, int y, int w, int h) {
        if (scroll == null) {
            scroll = new ClScrollPanel(x, y, w, h);
        } else {
            scroll.x = x;
            scroll.y = y;
            scroll.w = w;
            scroll.h = h;
        }
        scroll.setContentHeight(contentHeight);
    }

    protected void section(GuiGraphics g, Font font, String title, int x, int y, int w, float alpha) {
        g.drawString(font, title, x, y, CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT_HOVER, alpha), true);
        g.fill(x, y + 11, x + w, y + 12, CraftLiveTheme.withAlpha(CraftLiveTheme.BORDER, alpha * 0.7f));
    }

    protected int row(GuiGraphics g, Font font, String label, String value, int x, int y, int w, float alpha, int valueColor) {
        g.drawString(font, label, x, y, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, alpha), true);
        if (value != null) {
            String clipped = com.alpoyy.craftlive.render.ClRender.clip(font, value, w - 130);
            g.drawString(font, clipped, x + 130, y, CraftLiveTheme.withAlpha(valueColor, alpha), true);
        }
        return y + 14;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (ClButton b : buttons) if (b.mouseClicked(mouseX, mouseY, button)) return true;
        for (ClToggle t : toggles) if (t.mouseClicked(mouseX, mouseY, button)) return true;
        for (ClSlider s : sliders) if (s.mouseClicked(mouseX, mouseY, button)) return true;
        for (ClTextField f : fields) if (f.mouseClicked(mouseX, mouseY, button)) return true;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        scroll.scrollBy(-deltaY * 18);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int modifiers) {
        for (ClTextField f : fields) if (f.keyPressed(key, modifiers)) return true;
        return false;
    }

    @Override
    public boolean charTyped(char c) {
        for (ClTextField f : fields) if (f.charTyped(c)) return true;
        return false;
    }

    @Override
    public void mouseReleased() {
        for (ClSlider s : sliders) s.mouseReleased();
    }

    @Override
    public void tick() {
    }
}
