package com.alpoyy.craftlive.gui.tabs;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.gui.components.ClButton;
import com.alpoyy.craftlive.gui.components.ClSlider;
import com.alpoyy.craftlive.gui.components.ClToggle;
import com.alpoyy.craftlive.hud.HudWidget;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD Editor tab: widget list with visibility toggles, per-widget
 * scale/opacity for the selected widget, snap-to-grid, open editor.
 */
public class HudEditorTab extends TabBase {
    private final CraftLiveServices services;
    private HudWidget selected;

    public HudEditorTab(CraftLiveServices services) {
        this.services = services;
    }

    @Override
    public String id() {
        return "hud_editor";
    }

    @Override
    public String title() {
        return "HUD Editor";
    }

    @Override
    protected void build() {
        buttons.add(new ClButton(0, 0, 150, 20, "Buka Editor (J)", ClButton.Style.PRIMARY,
                b -> {
                    // close GUI then open the dedicated editor
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    mc.setScreen(new com.alpoyy.craftlive.hud.HudEditorScreen(services));
                }));
        buttons.add(new ClButton(156, 0, 140, 20, "Reset Semua Posisi", ClButton.Style.DANGER,
                b -> {
                    float y = 12;
                    for (HudWidget w : services.widgets.all()) {
                        w.resetLayout(12, y);
                        y += w.renderHeight() + 10;
                    }
                    services.widgets.saveAll();
                }));
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       double mouseX, double mouseY, float alpha, long epochMs) {
        layout(x, y, w, h);
        scroll.render(g, alpha, () -> {
            int cx = scroll.x;
            int cy = scroll.y + (int) scroll.offset() + 4;
            float a = alpha;

            for (ClButton b : buttons) b.render(g, font, mouseX, mouseY, a, true);
            cy += 28;

            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Tampilkan semua HUD (T)", () -> services.config.hudVisible,
                    v -> services.config.hudVisible = v));
            toggles.add(new ClToggle(cx + 200, cy, "Snap ke grid", () -> services.config.hudSnapToGrid,
                    v -> services.config.hudSnapToGrid = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 26;

            section(g, font, "Widget", cx, cy, scroll.w, a);
            cy += 18;

            // global scale/opacity
            sliders.clear();
            sliders.add(new ClSlider(cx, cy, 200, "Skala global", () -> (double) services.config.hudGlobalScale,
                    v -> services.config.hudGlobalScale = 0.5f + v * 1.5f));
            sliders.get(0).format = "%.2fx";
            sliders.get(0).render(g, font, mouseX, mouseY, a);
            cy += 34;
            sliders.add(new ClSlider(cx, cy, 200, "Opasitas global", () -> (double) services.config.hudGlobalOpacity,
                    v -> services.config.hudGlobalOpacity = Math.max(0.1f, v)));
            sliders.get(1).format = "%.0f%%";
            sliders.get(1).render(g, font, mouseX, mouseY, a);
            cy += 36;

            // widget list
            var widgetList = services.widgets.all();
            for (HudWidget widget : widgetList) {
                if (selected == widget) {
                    g.fill(cx - 2, cy - 3, cx + scroll.w - 2, cy + 15,
                            CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT, a * 0.18f));
                }
                g.drawString(font, widget.title, cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
                String state = widget.isVisible() ? "tampil" : "sembunyi";
                g.drawString(font, state, cx + 110, cy,
                        CraftLiveTheme.withAlpha(widget.isVisible() ? CraftLiveTheme.LIVE_GREEN : CraftLiveTheme.TEXT_DIM, a), true);
                ClToggle vis = new ClToggle(cx + 170, cy - 1, "Tampil", widget::isVisible, v -> {
                    widget.setVisible(v);
                    services.widgets.saveAll();
                });
                vis.render(g, font, mouseX, mouseY, a);
                ClButton select = new ClButton(cx + scroll.w - 90, cy - 2, 86, 14,
                        selected == widget ? "Terpilih" : "Pilih", ClButton.Style.SECONDARY, b -> {
                    selected = (selected == widget) ? null : widget;
                    for (HudWidget other : widgetList) other.setSelected(other == selected);
                });
                select.render(g, font, mouseX, mouseY, a, true);
                cy += 20;
            }
            cy += 6;

            // selected widget controls
            if (selected != null) {
                section(g, font, "Widget terpilih: " + selected.title, cx, cy, scroll.w, a);
                cy += 18;
                sliders.clear();
                sliders.add(new ClSlider(cx, cy, 200, "Skala", () -> (double) ((selected.effectiveScale() - 0.5f) / 1.5f),
                        v -> selected.setScale(0.5f + v * 1.5f)));
                sliders.get(0).format = "%.2fx";
                sliders.get(0).render(g, font, mouseX, mouseY, a);
                cy += 34;
                sliders.add(new ClSlider(cx, cy, 200, "Opasitas", () -> (double) selected.opacity(),
                        v -> selected.setOpacity(Math.max(0.1f, v))));
                sliders.get(1).format = "%.0f%%";
                sliders.get(1).render(g, font, mouseX, mouseY, a);
                cy += 36;
                ClButton resetOne = new ClButton(cx, cy, 120, 18, "Reset Posisi Widget", ClButton.Style.SECONDARY,
                        b -> {
                            selected.resetLayout(12, 12);
                            services.widgets.saveAll();
                        });
                resetOne.render(g, font, mouseX, mouseY, a, true);
                cy += 26;
            }

            contentHeight = cy - scroll.y + 8;
            scroll.setContentHeight(contentHeight);
        });
    }
}
