package dev.tentaworks.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Air timer counts the Air left at the rate it really goes, smoothed so state switches do not jump. */
class AirClockTest {
    private static final double IDLE = 0.5 / 80, MOVE = 0.5 / 20, CLIMB = 0.5 / 10, GRAB = 0.5 / 40;

    @Test
    void expectedRateWinsThenObservedThenCreate() {
        assertEquals(IDLE + GRAB, AirClock.target(IDLE + GRAB, 0.3), 1e-12, "a draining Rig ignores the drops seen");
        assertEquals(0.02, AirClock.target(0, 0.02), 1e-12, "Stowed Far Use sips: the drop rate seen");
        assertEquals(AirClock.CREATE_RATE, AirClock.target(0, 0), 1e-12, "a Far Use reach before any drop: Create's 1 Air a second");
    }

    @Test
    void timeIsAirOverRate() {
        assertEquals(288000, AirClock.ticks(1800, IDLE), "Netherite Idle, 1800 Air: 4 hours, not Create's 30 minutes");
        assertEquals(36000, AirClock.ticks(1800, CLIMB));
        assertEquals(1800 * 20, AirClock.ticks(1800, AirClock.CREATE_RATE));
        double thrift3 = IDLE * (1 - 3 * 0.15);
        assertEquals((int) Math.ceil(1800 / thrift3), AirClock.ticks(1800, thrift3), "Thrift III lasts longer, not slower ticking");
        assertEquals(0, AirClock.ticks(0, MOVE));
        assertEquals(0, AirClock.ticks(-5, MOVE));
        assertTrue(AirClock.ticks(1800, 0) > 1_000_000_000, "a zero rate never divides by zero");
        assertEquals(Integer.MAX_VALUE, AirClock.ticks(Integer.MAX_VALUE, 0), "nor overflows");
    }

    @Test
    void smoothingStartsAtTheTargetAndEasesASwitch() {
        assertEquals(MOVE, AirClock.smooth(-1, MOVE), 1e-12, "first tick shown: no ramp from zero");
        double r = MOVE;
        r = AirClock.smooth(r, CLIMB);
        assertTrue(r > MOVE && r < MOVE + (CLIMB - MOVE) * 0.1, "one tick moves a little: " + r);
        for (int i = 1; i < 20; i++) r = AirClock.smooth(r, CLIMB);
        assertTrue(r > MOVE + (CLIMB - MOVE) * 0.55 && r < MOVE + (CLIMB - MOVE) * 0.7, "one second: about two thirds of the way: " + r);
        for (int i = 0; i < 80; i++) r = AirClock.smooth(r, CLIMB);
        assertEquals(CLIMB, r, (CLIMB - MOVE) * 0.05, "after 5 seconds: there");
        double flicker = IDLE;
        for (int i = 0; i < 200; i++) flicker = AirClock.smooth(flicker, i % 2 == 0 ? IDLE : MOVE);
        assertEquals((IDLE + MOVE) / 2, flicker, (MOVE - IDLE) * 0.05, "a gait flicking Idle and Move settles on the mean");
    }
}
