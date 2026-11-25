package com.alpoyy.craftlive.gui.tabs;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.clock.ClockEngine;
import com.alpoyy.craftlive.gui.components.ClToggle;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.time.ZoneId;

/**
 * Clock tab: timezone, format, Minecraft-time toggles, dual mode, preview.
 * The Clock has no dedicated keybind (per spec).
 */
public class ClockTab extends TabBase {
    private final CraftLiveServices services;

    public ClockTab(CraftLiveServices services) {
        this.services = services;
    }

    @Override
    public String id() {
        return "clock";
    }

    @Override
    public String title() {
        return "Clock";
    }

    @Override
    protected void build() {
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       double mouseX, double mouseY, float alpha, long epochMs) {
        layout(x, y, w, h);
        scroll.render(g, alpha, () -> {
            int cx = scroll.x;
            int cy = scroll.y + (int) scroll.offset() + 4;
            float a = alpha;
            var cfg = services.config;

            // big preview
            ZoneId zone = ClockEngine.resolveZone(cfg.clockTimezone);
            String time = ClockEngine.formatReal(zone, cfg.clock24Hour, cfg.clockShowSeconds, epochMs);
            var f30 = font;
            g.drawString(f30, time, cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
            g.drawString(f30, ClockEngine.zoneLabel(zone), cx + f30.width(time) + 10, cy + 4,
                    CraftLiveTheme.withAlpha(CraftLiveTheme.ACCENT_HOVER, a), true);
            cy += 22;

            section(g, font, "Zona Waktu", cx, cy, scroll.w, a);
            cy += 18;
            int col = 0;
            for (String z : ClockEngine.COMMON_ZONES) {
                boolean selected = z.equals(cfg.clockTimezone) || (cfg.clockTimezone.isBlank() && z.equals("Asia/Jakarta"));
                int bx = cx + col * 130;
                var btn = new com.alpoyy.craftlive.gui.components.ClButton(bx, cy, 124, 16,
                        ClockEngine.zoneLabel(ZoneId.of(z)), selected ? com.alpoyy.craftlive.gui.components.ClButton.Style.PRIMARY
                        : com.alpoyy.craftlive.gui.components.ClButton.Style.SECONDARY,
                        b -> {
                            cfg.clockTimezone = z;
                            services.configManager.saveAsync();
                        });
                btn.render(g, font, mouseX, mouseY, a, true);
                col++;
                if (col == 3) {
                    col = 0;
                    cy += 20;
                }
            }
            if (col != 0) cy += 20;
            cy += 4;

            section(g, font, "Format", cx, cy, scroll.w, a);
            cy += 18;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Format 24 jam", () -> cfg.clock24Hour, v -> cfg.clock24Hour = v));
            toggles.add(new ClToggle(cx + 200, cy, "Tampilkan detik", () -> cfg.clockShowSeconds, v -> cfg.clockShowSeconds = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 26;

            section(g, font, "Waktu Minecraft (dunia aktif)", cx, cy, scroll.w, a);
            cy += 18;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Tampilkan waktu MC", () -> cfg.clockShowMinecraftTime, v -> cfg.clockShowMinecraftTime = v));
            toggles.add(new ClToggle(cx + 200, cy, "Penghitung hari", () -> cfg.clockShowDayCounter, v -> cfg.clockShowDayCounter = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 24;
            toggles.clear();
            toggles.add(new ClToggle(cx, cy, "Indikator siang/malam", () -> cfg.clockShowDayNightIndicator, v -> cfg.clockShowDayNightIndicator = v));
            toggles.add(new ClToggle(cx + 200, cy, "Mode ganda (real + MC)", () -> cfg.clockDualMode, v -> cfg.clockDualMode = v));
            for (ClToggle t : toggles) t.render(g, font, mouseX, mouseY, a);
            cy += 26;

            // live MC time preview
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null) {
                long dayTime = mc.level.getDayTime();
                g.drawString(font, "MC sekarang: " + ClockEngine.describeMinecraft(dayTime)
                                + "  •  " + ClockEngine.formatMinecraft(dayTime, true, false),
                        cx, cy, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
                cy += 14;
            } else {
                g.drawString(font, "MC: belum ada dunia dimuat", cx, cy,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.7f), true);
                cy += 14;
            }

            contentHeight = cy - scroll.y + 8;
            scroll.setContentHeight(contentHeight);
        });
    }
}
