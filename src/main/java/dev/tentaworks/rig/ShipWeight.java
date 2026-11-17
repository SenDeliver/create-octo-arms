package dev.tentaworks.rig;

/**
 * How heavy a held Ship's Build feels (CONTEXT: Ship Grab): {@code x = ln(1 + m/knee) / ln(1 + cap/knee)}, clamped to 0..1, with
 * {@code m} the Build's Sable mass and {@code cap} the tier's mass cap; each channel runs at {@code 1 − (1 − floor)·x}, so a light
 * Ship is almost free and one at the cap runs at its floor. Channels: the hold servo (gain and top speed), the wearer's walking
 * and Rig Speed (on top of {@code grab.heavySpeedFactor}), the Turn rate and the Throw speed. Factors are kept to
 * {@value #STEP}, so the server and the wearer's client use the same numbers. A Build heavier than {@value #HYSTERESIS} × the cap
 * is let go.
 */
public final class ShipWeight {
    public static final double STEP = 0.01;
    public static final double HYSTERESIS = 1.05;

    /** The factors of one hold, each 0..1: hold servo, walking (times {@code grab.heavySpeedFactor}), Turn, Throw. */
    public record Factors(double servo, double walk, double turn, double hurl) {
        public static final Factors NONE = new Factors(1, 1, 1, 1);
    }

    /** The Server Config's knee (mass where the curve bends) and floors (each channel at the cap). */
    public record Curve(double knee, double servo, double walk, double turn, double hurl) {
    }

    private ShipWeight() {
    }

    /** How far {@code mass} is along the curve to {@code cap}: 0 light, 1 at or over the cap. */
    public static double x(double mass, double cap, double knee) {
        if (!(mass > 0)) return 0;
        if (!(cap > 0)) return 1;
        double k = Math.max(knee, 1.0E-6);
        double x = Math.log1p(mass / k) / Math.log1p(cap / k);
        return Math.max(0, Math.min(1, x));
    }

    /** One channel at {@code x}: 1 light, {@code floor} at the cap. */
    public static double factor(double x, double floor) {
        return 1 - (1 - floor) * x;
    }

    /** {@code f} to the nearest {@value #STEP}, never below {@value #STEP}. */
    public static double quantise(double f) {
        return Math.max(STEP, Math.round(f / STEP) * STEP);
    }

    /** Every channel for a Build of {@code mass} under {@code cap}, quantised. */
    public static Factors of(double mass, double cap, Curve c) {
        double x = x(mass, cap, c.knee());
        return new Factors(quantise(factor(x, c.servo())), quantise(factor(x, c.walk())), quantise(factor(x, c.turn())),
                quantise(factor(x, c.hurl())));
    }

    /** A held Build of {@code mass} is past the cap with its hysteresis: it is let go. */
    public static boolean over(double mass, double cap) {
        return mass > cap * HYSTERESIS;
    }

    /** Ticks in a row beyond the far limit before a hold with this servo factor ends: {@code ticks} for a light one, more for a slow heavy one. */
    public static int farTicks(int ticks, double servo) {
        return (int) Math.ceil(ticks / Math.max(STEP, Math.min(1, servo)) - 1.0E-9);
    }
}
