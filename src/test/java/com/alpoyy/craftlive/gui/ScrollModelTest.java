package com.alpoyy.craftlive.gui;

import com.alpoyy.craftlive.gui.components.ScrollModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scrolling regression tests — the panel scroll math must:
 * never overscroll, reach both ends, survive content shrink, and
 * smooth-approach the wheel target without jumping past bounds.
 *
 * Convention: scrollBy(+delta) scrolls DEEPER into content (offset grows),
 * scrollBy(-delta) scrolls back up. This matches the GUI layer which calls
 * scroll.scrollBy(-mouseWheelDelta * step).
 */
class ScrollModelTest {
    /** Run update() until the offset converges on the target (bounded). */
    private static void settle(ScrollModel m) {
        for (int i = 0; i < 500; i++) {
            m.update();
            if (Math.abs(m.target() - m.offset()) < 0.4f) break;
        }
        m.update();
    }

    @Test
    void noScrollWhenContentFits() {
        ScrollModel m = new ScrollModel();
        m.setViewport(300);
        m.setContentHeight(200);
        m.scrollBy(100);
        settle(m);
        assertEquals(0, m.offset(), 0.001f);
        assertFalse(m.scrollable());
        assertEquals(0, m.maxScroll(), 0.001f);
    }

    @Test
    void wheelScrollsAndClampsToBounds() {
        ScrollModel m = new ScrollModel();
        m.setViewport(300);
        m.setContentHeight(1000); // max 700
        m.scrollBy(5000);         // huge scroll down
        settle(m);
        assertEquals(700, m.offset(), 0.5f, "must clamp at bottom");
        m.scrollBy(-5000);        // huge scroll up
        settle(m);
        assertEquals(0, m.offset(), 0.5f, "must clamp at top");
    }

    @Test
    void smoothApproachDoesNotOvershoot() {
        ScrollModel m = new ScrollModel();
        m.setViewport(100);
        m.setContentHeight(500); // max 400
        m.scrollBy(400);
        float prev = m.offset();
        for (int i = 0; i < 50; i++) {
            m.update();
            assertTrue(m.offset() >= prev - 0.001f, "must move monotonically toward target");
            assertTrue(m.offset() <= 400f, "must never exceed max");
            prev = m.offset();
        }
        assertEquals(400, m.offset(), 0.5f);
    }

    @Test
    void shrinkContentReclampsOffset() {
        ScrollModel m = new ScrollModel();
        m.setViewport(300);
        m.setContentHeight(1000);
        m.scrollBy(700);
        settle(m);
        assertEquals(700, m.offset(), 0.5f);
        m.setContentHeight(400); // content shrinks below current offset
        settle(m);
        assertTrue(m.offset() <= 100f, "offset must reclamp to new max");
    }

    @Test
    void growViewportReclamps() {
        ScrollModel m = new ScrollModel();
        m.setViewport(100);
        m.setContentHeight(1000);
        m.scrollBy(900);
        settle(m);
        assertEquals(900, m.offset(), 0.5f);
        m.setViewport(600); // bigger window
        settle(m);
        assertTrue(m.offset() <= 400f, "offset must reclamp when viewport grows");
    }

    @Test
    void resetReturnsToTop() {
        ScrollModel m = new ScrollModel();
        m.setViewport(100);
        m.setContentHeight(1000);
        m.scrollBy(300);
        settle(m);
        m.reset();
        assertEquals(0, m.offset(), 0.001f);
    }

    @Test
    void positiveAndNegativeWheelBothWork() {
        ScrollModel m = new ScrollModel();
        m.setViewport(100);
        m.setContentHeight(1000);
        m.scrollBy(50);
        settle(m);
        assertEquals(50, m.offset(), 0.5f);
        m.scrollBy(-20);
        settle(m);
        assertEquals(30, m.offset(), 0.5f);
    }

    @Test
    void bottomOfContentIsReachable() {
        ScrollModel m = new ScrollModel();
        m.setViewport(100);
        m.setContentHeight(1000);
        m.scrollBy(m.maxScroll());
        settle(m);
        assertEquals(900, m.offset(), 0.5f, "bottom must be exactly reachable");
    }
}
