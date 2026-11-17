package dev.tentaworks.rig;

import java.util.Arrays;

/**
 * The server's speed check on a carried wearer's real move, over a window of {@value #WINDOW} server ticks. The allowed
 * distance follows the wearer's move packets (one per client tick), not server ticks, so a server below 20 TPS or one
 * catching up after a stall gets several client ticks of move per server tick. The packets count only up to the real
 * time of the window, so extra packets buy no speed, and never below {@value #WINDOW}.
 * A violation is the window over its allowance for more than {@value #LIMIT} ticks in a row: a lump of move that lands in
 * a few ticks (a lag backlog) leaves the window before that, sustained overspeed does not.
 */
public final class SpeedCheck {
    public static final int WINDOW = 10;
    static final int LIMIT = 2 * WINDOW;
    private static final double SLACK = 1.25;
    private static final double EXTRA = 1.0;
    private final double[] moves = new double[WINDOW];
    private final int[] packets = new int[WINDOW];
    /** Real time per server tick, in ticks of 50 ms. */
    private final double[] times = new double[WINDOW];
    private int at;
    private int over;
    private double sum;
    private int packetSum;
    private double timeSum;

    /** One server tick: the real move, the move packets that came in, the real time it took. */
    public void add(double move, int packetCount, double time) {
        moves[at] = move;
        packets[at] = packetCount;
        times[at] = time;
        at = (at + 1) % WINDOW;
        sum = 0;
        packetSum = 0;
        timeSum = 0;
        for (int i = 0; i < WINDOW; i++) {
            sum += moves[i];
            packetSum += packets[i];
            timeSum += times[i];
        }
    }

    public double steps() {
        return Math.max(WINDOW, Math.min(packetSum, timeSum));
    }

    public boolean over(double maxStep) {
        return sum > maxStep * steps() * SLACK + EXTRA;
    }

    /** A carried tick: true once the window has been over for more than {@value #LIMIT} ticks in a row. */
    public boolean count(double maxStep) {
        over = over(maxStep) ? over + 1 : 0;
        return over > LIMIT;
    }

    /** Not carried, or in a grace: the run of over ticks starts again. */
    public void calm() {
        over = 0;
    }

    /** The moves start over (top Speed changed or a violation was handled); packets and time stay. */
    public void clearMoves() {
        Arrays.fill(moves, 0);
        sum = 0;
        over = 0;
    }

    /** A teleport: everything starts over. */
    public void reset() {
        Arrays.fill(moves, 0);
        Arrays.fill(packets, 0);
        Arrays.fill(times, 0);
        sum = 0;
        packetSum = 0;
        timeSum = 0;
        over = 0;
    }

    public double sum() {
        return sum;
    }

    public int packets() {
        return packetSum;
    }

    public double time() {
        return timeSum;
    }

    public int overTicks() {
        return over;
    }
}
