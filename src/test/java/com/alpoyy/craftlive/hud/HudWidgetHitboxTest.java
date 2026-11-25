package com.alpoyy.craftlive.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for HUD Editor hit synchronization (mclo.gs-reported
 * "hitboxes don't match rendered widgets"): the rendered size includes the
 * GLOBAL HUD scale, so the hitbox must too. Also covers screen clamping
 * with a null Minecraft window (entrypoint-time safety).
 */
class HudWidgetHitboxTest {
    private static HudWidget widget(float x, float y, int w, int h) {
        return new HudWidget("test", "Test", x, y, w, h) {
            @Override
            protected void drawContent(net.minecraft.client.gui.GuiGraphics g, int w, int h,
                                       float alpha, boolean editorMode, long epochMs) {
                // no-op for hitbox math tests
            }
        };
    }

    @Test
    void renderSizeIncludesGlobalScale() {
        HudWidget w = widget(10, 10, 200, 100);
        w.setScale(1f);
        w.setLastGlobalScale(1f);
        assertEquals(200f, w.renderWidth(), 0.001f);
        assertEquals(100f, w.renderHeight(), 0.001f);

        w.setLastGlobalScale(1.5f);
        assertEquals(300f, w.renderWidth(), 0.001f, "hitbox must include the global HUD scale");
        assertEquals(150f, w.renderHeight(), 0.001f);
    }

    @Test
    void hitboxMatchesRenderedRectAtGlobalScale() {
        HudWidget w = widget(50, 60, 200, 100);
        w.setScale(1f);
        w.setLastGlobalScale(1.5f);
        // inside the rendered rect (50..350, 60..210)
        assertTrue(w.isMouseOver(55, 65));
        assertTrue(w.isMouseOver(340, 200));
        assertFalse(w.isMouseOver(360, 100), "beyond rendered width must miss");
        assertFalse(w.isMouseOver(100, 220), "beyond rendered height must miss");
    }

    @Test
    void perWidgetScaleMultipliesWithGlobal() {
        HudWidget w = widget(0, 0, 100, 50);
        w.setScale(2f);
        w.setLastGlobalScale(1.5f);
        assertEquals(300f, w.renderWidth(), 0.001f);
        assertTrue(w.isMouseOver(299, 74));
        assertFalse(w.isMouseOver(301, 10));
    }

    @Test
    void ownScaleGetterUnaffectedByGlobal() {
        HudWidget w = widget(0, 0, 100, 50);
        w.setScale(1.25f);
        w.setLastGlobalScale(2f);
        assertEquals(1.25f, w.effectiveScale(), 0.001f, "the scale slider must keep showing the per-widget scale");
    }

    @Test
    void clampNullSafeAndWithinBounds() {
        HudWidget w = widget(-500, -500, 200, 100);
        w.setLastGlobalScale(1f);
        // headless/entrypoint safety: Minecraft.getInstance() is null here —
        // clamp must not throw and must not change anything it cannot know
        assertDoesNotThrow(w::clampToScreen);
        // with a null window nothing is known: position must stay untouched
        assertEquals(-500f, w.getX(), 0.001f);
    }

    @Test
    void dragFollowsMouseWithoutJumping() {
        HudWidget w = widget(100, 100, 100, 50);
        w.setLastGlobalScale(1f);
        w.startDrag(120, 110); // grabbed 20,10 inside the widget
        w.dragTo(180, 160, false, 8);
        assertEquals(160f, w.getX(), 0.001f, "widget must follow the mouse delta exactly");
        assertEquals(150f, w.getY(), 0.001f);
        w.stopDrag();
        assertFalse(w.isDragging(), "dragging state must be released");
    }
}
