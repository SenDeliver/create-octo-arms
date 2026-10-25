package dev.tentaworks.client;

/**
 * The drawn Step phase of one Tentacle slot (0 lifted, 1 planted). It follows the phase on the wire but never freezes on a
 * missed packet, never jumps when a Step is first seen already done (a mid-air catch, Activation, a remote Rig under packet
 * loss) and restarts from 0 when the target changes mid-arc. The wearer's own gait, which reports every tick, is reproduced exactly.
 */
final class StepPhase {
    /** Arc per tick when the wire shows the Step already done: the arc is driven locally. */
    static final float DRIVE = 0.25F;
    /** Arc per tick while the wire reports no progress: the slowest gait Step (7 ticks). */
    static final float MIN_RATE = 1 / 7F;

    float phase = 1, phaseO = 1;
    private float wire = 1;
    private boolean driven;

    float at(float pt) {
        return phaseO + (phase - phaseO) * pt;
    }

    boolean stepping() {
        return phase < 1;
    }

    /** A new target this tick: the arc starts at 0. A wire phase already at 1 means the Step start was never seen: drive it. */
    void start(float wirePhase) {
        phaseO = 0;
        phase = 0;
        wire = wirePhase;
        driven = wirePhase >= 1;
    }

    /** Same target as last tick. Returns true when the arc completes this tick. */
    boolean advance(float wirePhase) {
        phaseO = phase;
        if (phase >= 1) {
            wire = wirePhase;
            return false;
        }
        float step;
        if (driven) step = DRIVE;
        else {
            step = wirePhase - wire;
            if (wirePhase >= 1) step = Math.max(step, DRIVE);
            else if (step <= 0) step = MIN_RATE;
        }
        wire = wirePhase;
        phase = Math.min(1, phase + step);
        if (phase >= 1) driven = false;
        return phase >= 1;
    }

    /** No target: planted and still. */
    void settle() {
        phaseO = phase;
        phase = 1;
        wire = 1;
        driven = false;
    }
}
