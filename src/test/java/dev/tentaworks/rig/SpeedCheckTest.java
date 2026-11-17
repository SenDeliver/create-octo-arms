package dev.tentaworks.rig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeedCheckTest {
    private static final double MAX = 8 / 20.0 * 1.05;

    /** Feeds {@code ticks} ticks and returns the tick (1-based) of the first violation, or -1. */
    private static int run(SpeedCheck c, int ticks, double move, int packets, double time) {
        for (int i = 1; i <= ticks; i++) {
            c.add(move, packets, time);
            if (c.count(MAX)) return i;
        }
        return -1;
    }

    @Test
    void topSpeedNeverViolates() {
        assertTrue(run(new SpeedCheck(), 400, MAX, 1, 1) < 0);
    }

    @Test
    void lumpInTwoTicksLeavesTheWindow() {
        SpeedCheck c = new SpeedCheck();
        run(c, 20, MAX, 1, 1);
        int maxOver = 0;
        for (int i = 0; i < 2; i++) {
            c.add(MAX + 5, 1, 1);
            assertFalse(c.count(MAX));
            maxOver = Math.max(maxOver, c.overTicks());
        }
        for (int i = 0; i < 40; i++) {
            c.add(MAX, 1, 1);
            assertFalse(c.count(MAX), "tick " + i);
            maxOver = Math.max(maxOver, c.overTicks());
        }
        assertTrue(maxOver > 10, "the old check (over > 10) stowed here: " + maxOver);
    }

    @Test
    void backlogAfterSilenceIsCredited() {
        SpeedCheck c = new SpeedCheck();
        run(c, 20, MAX, 1, 1);
        assertTrue(run(c, 15, 0, 0, 1) < 0);
        c.add(MAX * 15, 15, 1);
        assertFalse(c.count(MAX));
        assertTrue(run(c, 60, MAX, 1, 1) < 0);
    }

    @Test
    void stalledServerTickCountsItsRealTime() {
        SpeedCheck c = new SpeedCheck();
        run(c, 20, MAX, 1, 1);
        c.add(MAX * 30, 30, 30);
        assertFalse(c.count(MAX));
        assertTrue(run(c, 60, MAX, 1, 1) < 0);
    }

    @Test
    void sustainedOverspeedViolates() {
        int at = run(new SpeedCheck(), 200, MAX * 2, 1, 1);
        assertTrue(at > SpeedCheck.LIMIT && at < 2 * SpeedCheck.LIMIT, "violation at tick " + at);
    }

    @Test
    void extraPacketsBuyNoSpeed() {
        assertTrue(run(new SpeedCheck(), 200, MAX * 2, 5, 1) > 0);
    }

    @Test
    void calmAndClearStartTheRunOver() {
        SpeedCheck c = new SpeedCheck();
        run(c, SpeedCheck.LIMIT, MAX * 2, 1, 1);
        c.calm();
        c.add(MAX * 2, 1, 1);
        assertFalse(c.count(MAX));
        c.clearMoves();
        c.add(MAX, 1, 1);
        assertFalse(c.over(MAX));
    }
}
