package com.alpoyy.craftlive.gui.tabs;

import com.alpoyy.craftlive.gui.components.ClButton;
import com.alpoyy.craftlive.gui.components.ClScrollPanel;
import com.alpoyy.craftlive.gui.components.ClSlider;
import com.alpoyy.craftlive.gui.components.ClTextField;
import com.alpoyy.craftlive.gui.components.ClToggle;
import com.alpoyy.craftlive.sound.UISounds;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared tab plumbing: scroll panel, control lists, section headers.
 *
 * IMPORTANT hit-test contract: controls live in CONTENT space (they are
 * drawn inside the scroll panel translated by -offset). Mouse events arrive
 * in SCREEN space, so all hit-testing converts Y by adding scroll.offset()
 * and only reacts when the cursor is inside the visible panel — otherwise
 * buttons/toggles/sliders stop working the moment the panel is scrolled.
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
        g.drawString(font, title, x, y, CraftLiveTheme.withAlpha(CraftLiveTheme.accentHover(), alpha), true);
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

    // ---- input: screen space -> content space ----

    /** True when (mx,my) is inside the visible scroll panel. */
    protected boolean isOverPanel(double mouseX, double mouseY) {
        return scroll != null && scroll.isOver(mouseX, mouseY);
    }

    /** Convert a screen-space Y to content space for hit-testing. */
    protected double toContentY(double screenY) {
        return screenY + (scroll == null ? 0 : scroll.offset());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOverPanel(mouseX, mouseY)) return false;
        double cy = toContentY(mouseY);
        for (ClButton b : buttons) {
            if (b.mouseClicked(mouseX, cy, button)) {
                if (b.enabled) UISounds.click();
                return true;
            }
        }
        for (ClToggle t : toggles) {
            if (t.mouseClicked(mouseX, cy, button)) {
                UISounds.toggle(!toggleStateForSound(t));
                return true;
            }
        }
        for (ClSlider s : sliders) {
            if (s.mouseClicked(mouseX, cy, button)) {
                UISounds.click();
                return true;
            }
        }
        for (ClTextField f : fields) if (f.mouseClicked(mouseX, cy, button)) return true;
        return false;
    }

    /** Best-effort ON/OFF distinction for toggle sounds. */
    private boolean toggleStateForSound(ClToggle t) {
        try {
            return t.isOnForTest();
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!isOverPanel(mouseX, mouseY) && !anySliderDragging()) return false;
        double cy = toContentY(mouseY);
        for (ClSlider s : sliders) {
            if (s.mouseDragged(mouseX)) return true;
        }
        return false;
    }

    private boolean anySliderDragging() {
        for (ClSlider s : sliders) {
            if (s.isDraggingForTest()) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaY) {
        // only the panel under the cursor scrolls
        if (!isOverPanel(mouseX, mouseY)) return false;
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
