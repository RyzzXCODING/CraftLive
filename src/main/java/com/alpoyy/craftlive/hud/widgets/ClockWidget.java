package com.alpoyy.craftlive.hud.widgets;

import com.alpoyy.craftlive.clock.ClockEngine;
import com.alpoyy.craftlive.hud.HudWidget;
import com.alpoyy.craftlive.render.ClRender;
import com.alpoyy.craftlive.theme.CraftLiveTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.time.ZoneId;

/**
 * Floating Clock widget: real-world time (configurable timezone, 12/24h),
 * optional Minecraft world time, day counter and day/night indicator.
 * Minecraft time uses the ACTUAL world time from the loaded level.
 */
public class ClockWidget extends HudWidget {
    public ClockWidget(float x, float y) {
        super("clock", "Clock", x, y, 150, 56);
    }

    @Override
    protected void drawContent(GuiGraphics g, int w, int h, float alpha, boolean editorMode, long epochMs) {
        var config = com.alpoyy.craftlive.CraftLiveServices.get().config;
        drawWidgetBackground(g, w, h, alpha, false);

        Font font = font();
        int pad = 8;
        float a = alpha;

        ZoneId zone = ClockEngine.resolveZone(config.clockTimezone);
        String realTime = ClockEngine.formatReal(zone, config.clock24Hour, config.clockShowSeconds, epochMs);
        String zoneLabel = ClockEngine.zoneLabel(zone);

        g.drawString(font, realTime, pad, 6, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT, a), true);
        g.drawString(font, zoneLabel, w - font.width(zoneLabel) - pad, 8,
                CraftLiveTheme.withAlpha(CraftLiveTheme.accentHover(), a), true);

        if (editorMode) {
            g.drawString(font, "☾ Hari 7 — Malam", pad, 22, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            g.drawString(font, "MC 09:41", pad, 36, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            return;
        }

        // Minecraft world time (real data from the loaded level)
        Minecraft mc = Minecraft.getInstance();
        if (config.clockShowMinecraftTime) {
            if (mc.level != null) {
                long dayTime = mc.level.getDayTime();
                String line = "";
                if (config.clockShowDayNightIndicator) {
                    line += ClockEngine.dayNightIcon(dayTime) + " ";
                }
                line += ClockEngine.formatMinecraft(dayTime, true, false);
                if (config.clockShowDayCounter) {
                    line = ClockEngine.describeMinecraft(dayTime) + "  •  " + line;
                }
                g.drawString(font, ClRender.clip(font, line, w - pad * 2), pad, 22,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a), true);
            } else {
                g.drawString(font, "MC — tidak ada dunia", pad, 22,
                        CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.7f), true);
            }
        }

        // date line
        String date = java.time.LocalDate.now(zone)
                .format(java.time.format.DateTimeFormatter.ofPattern("EEE, dd MMM"));
        g.drawString(font, date, pad, h - 12, CraftLiveTheme.withAlpha(CraftLiveTheme.TEXT_DIM, a * 0.8f), true);
    }
}
