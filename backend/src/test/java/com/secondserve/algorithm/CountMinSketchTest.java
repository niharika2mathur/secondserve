package com.secondserve.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CountMinSketchTest {

    @Test @DisplayName("CM-1 sizing: eps=0.01, delta=0.01 -> width 272, depth 5")
    void sizing() {
        CountMinSketch c = new CountMinSketch(0.01, 0.01);
        assertEquals(272, c.width()); assertEquals(5, c.depth());
        assertEquals(272L * 5 * 8, c.memoryBytes());
    }

    @Test @DisplayName("CM-2 exact on a single key; unknown key in an empty sketch is 0")
    void single() {
        CountMinSketch c = new CountMinSketch(0.01, 0.01);
        assertEquals(0, c.estimate("nobody"));
        c.update("A", 5); c.update("A", 7);
        assertEquals(12, c.estimate("A"));
        assertEquals(12, c.total());
    }

    @Test @DisplayName("CM-3 never underestimates, and the eps*N error bound is met for >= 99% of keys (50,000 events, 2,000 keys)")
    void accuracy() {
        CountMinSketch c = new CountMinSketch(0.005, 0.01);
        Map<String, Long> exact = new HashMap<>();
        Random rnd = new Random(3);
        for (int i = 0; i < 50_000; i++) {
            String k = "K" + (int) Math.min(1999, Math.abs(rnd.nextGaussian() * 400));
            long w = 1 + rnd.nextInt(5);
            c.update(k, w); exact.merge(k, w, Long::sum);
        }
        int within = 0;
        for (Map.Entry<String, Long> e : exact.entrySet()) {
            long est = c.estimate(e.getKey());
            assertTrue(est >= e.getValue(), "count-min can only over-estimate");
            if (est - e.getValue() <= c.errorBound()) within++;
        }
        assertTrue(within >= exact.size() * 0.99, "bound held for " + within + " of " + exact.size());
    }

    @Test @DisplayName("CM-4 invalid parameters")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> new CountMinSketch(0, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new CountMinSketch(0.1, 1));
        CountMinSketch c = new CountMinSketch(0.1, 0.1);
        assertThrows(IllegalArgumentException.class, () -> c.update("", 1));
        assertThrows(IllegalArgumentException.class, () -> c.update("x", -1));
    }
}
