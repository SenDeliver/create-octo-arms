package dev.tentaworks.client;

/**
 * The remaining-Air time of {@link RigAirHud}: Air over the rate it really goes at. The expected rate (Idle, Move or Climb, tier,
 * Thrift, plus a Grab's cost) when the Rig or a hold drains; else the drop rate seen lately (Far Use sips, Create tools), else
 * Create's diving rate. Smoothed over about {@value #SMOOTH_TICKS} ticks, so a switch between Idle, Move and Climb does not make the
 * time jump.
 */
public final class AirClock {
    public static final double SMOOTH_TICKS = 20;
    /** Create's diving timer: 1 Air a second. */
    public static final double CREATE_RATE = 1.0 / 20;
    private static final double MIN_RATE = 1.0E-6;

    private AirClock() {
    }

    /** Air per tick the timer counts with: {@code expected} when the Rig or a hold drains, else {@code observed}, else Create's rate. */
    public static double target(double expected, double observed) {
        if (expected > 0) return expected;
        return observed > 0 ? observed : CREATE_RATE;
    }

    /** One tick of smoothing toward {@code target}; a {@code rate} below zero (the timer just showed) starts at the target. */
    public static double smooth(double rate, double target) {
        if (rate < 0) return target;
        return rate + (target - rate) * (1 - Math.exp(-1 / SMOOTH_TICKS));
    }

    /** Ticks {@code air} lasts at {@code rate} Air per tick. */
    public static int ticks(int air, double rate) {
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(Math.max(0, air) / Math.max(rate, MIN_RATE)));
    }
}
