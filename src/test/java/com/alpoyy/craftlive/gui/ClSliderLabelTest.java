package com.alpoyy.craftlive.gui;

import com.alpoyy.craftlive.gui.components.ClSlider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the Lyrics-tab crash (mclo.gs/szAQvxL):
 * a "%d"-style format with a float slider value used to throw
 * IllegalFormatConversionException inside render() and crash the game.
 */
class ClSliderLabelTest {
    private final Atomicish state = new Atomicish();

    private ClSlider slider(String format) {
        ClSlider s = new ClSlider(0, 0, 100, "test", () -> 0.5, v -> state.value = v);
        s.format = format;
        return s;
    }

    @Test
    void intFormatWithFloatValueFallsBackInsteadOfThrowing() {
        ClSlider s = slider("%+d ms"); // the exact crashing format
        assertDoesNotThrow(() -> s.labelForTest(0.25f));
        assertNotEquals("", s.labelForTest(0.25f));
    }

    @Test
    void floatFormatStillWorks() {
        ClSlider s = slider("%.2fx");
        assertEquals("0.50x", s.labelForTest(0.5f));
    }

    @Test
    void garbageFormatFallsBackToNumber() {
        ClSlider s = slider("%%%q-broken");
        assertDoesNotThrow(() -> s.labelForTest(0.75f));
        assertEquals("0.75", s.labelForTest(0.75f));
    }

    @Test
    void labelFormatterTakesPrecedence() {
        ClSlider s = slider("%.2f");
        s.labelFormatter = v -> String.format("%+d ms", (int) Math.round((v - 0.5) * 20000));
        // float imprecision: assert within ±2 ms instead of exact values
        int at40 = Integer.parseInt(s.labelForTest(0.4f).replace(" ms", "").replace("+", ""));
        int at60 = Integer.parseInt(s.labelForTest(0.6f).replace(" ms", "").replace("+", ""));
        assertTrue(Math.abs(at40 + 2000) <= 2, "got " + at40);
        assertTrue(Math.abs(at60 - 2000) <= 2, "got " + at60);
        assertEquals("+0 ms", s.labelForTest(0.5f));   // %+d shows the sign
    }

    @Test
    void throwingFormatterFallsBackGracefully() {
        ClSlider s = slider("%.2f");
        s.labelFormatter = v -> { throw new RuntimeException("boom"); };
        assertDoesNotThrow(() -> s.labelForTest(0.5f));
        assertEquals("0.50", s.labelForTest(0.5f));
    }

    /** tiny mutable holder (tests can't use AtomicBoolean for float). */
    static class Atomicish {
        float value;
    }
}
