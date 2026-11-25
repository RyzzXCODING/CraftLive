package com.alpoyy.craftlive.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnimationTest {
    @Test
    void approachesTarget() {
        Animation anim = new Animation(0f, 8f);
        anim.setTarget(1f);
        float last = 0f;
        for (int i = 0; i < 100; i++) {
            anim.update(16);
            assertTrue(anim.value() >= last - 0.0001f, "must move toward target monotonically");
            last = anim.value();
        }
        assertEquals(1f, anim.value(), 0.01f);
        assertTrue(anim.settled());
    }

    @Test
    void frameRateIndependent() {
        // 60 updates of 16ms ≈ 10 updates of ~96ms
        Animation a = new Animation(0f, 5f);
        a.setTarget(1f);
        for (int i = 0; i < 60; i++) a.update(16);
        Animation b = new Animation(0f, 5f);
        b.setTarget(1f);
        for (int i = 0; i < 10; i++) b.update(96);
        assertEquals(a.value(), b.value(), 0.02f, "animation must be FPS-independent");
    }

    @Test
    void bigDeltaDoesNotExplode() {
        Animation anim = new Animation(0f, 8f);
        anim.setTarget(1f);
        anim.update(60_000); // huge frame gap clamped
        assertEquals(1f, anim.value(), 0.001f);
    }

    @Test
    void jumpSnaps() {
        Animation anim = new Animation(0f, 8f);
        anim.jump(0.5f);
        assertEquals(0.5f, anim.value(), 0.0001f);
        assertEquals(0.5f, anim.target(), 0.0001f);
    }

    @Test
    void progressClamps() {
        assertEquals(0f, Animation.progress(0, 1000));
        assertEquals(0.5f, Animation.progress(500, 1000), 0.001f);
        assertEquals(1f, Animation.progress(1500, 1000));
        assertEquals(1f, Animation.progress(0, 0));
    }

    @Test
    void easeOutRange() {
        assertEquals(0f, Animation.easeOut(0f), 0.001f);
        assertEquals(1f, Animation.easeOut(1f), 0.001f);
        assertTrue(Animation.easeOut(0.5f) > 0.5f);
        assertEquals(0f, Animation.easeOut(-1f), 0.001f);
        assertEquals(1f, Animation.easeOut(2f), 0.001f);
    }

    @Test
    void livePulseBounded() {
        for (long t = 0; t < 10_000; t += 137) {
            float p = Animation.livePulse(t);
            assertTrue(p >= 0f && p <= 1f, "pulse out of range at " + t + ": " + p);
        }
    }
}
