package dev.tentaworks.rig;

import java.util.function.BooleanSupplier;

/**
 * Who the Actuators may Grab, as a pure function of what the server sees (CONTEXT: Grab). A living mob, never a player,
 * never a boss ({@code tentaworks:bosses} or over {@value #BOSS_HEALTH} max health), nothing riding or ridden, nothing held
 * by another wearer, another player's tamed, owned or leashed animal only with {@code grab.tamedOthers}; some point of its box
 * within Reach + {@value #SLACK} of the body ({@link ActuatorReach}) and in sight of it. A light target ({@link #light}) takes
 * one free Actuator ({@link #arm}: the off-hand one first), a heavy one or a Ship both, and both hands must then be empty. A claim
 * or protection mod may still say no ({@link #claimed}).
 */
public final class GrabRule {
    public static final double LIGHT_WIDTH = 1.5;
    public static final double LIGHT_HEIGHT = 2.0;
    /** Width × width × height: keeps a horse (1.4 × 1.6) heavy while a spider (1.4 × 0.9) stays light. */
    public static final double LIGHT_BULK = 2.0;
    public static final double BOSS_HEALTH = 100.0;
    public static final double SLACK = 0.75;
    private static final double EPS = 1.0E-6;

    /** What the server knows of a target; {@code distance} from the wearer's body to the nearest point of its box. */
    public record Target(boolean living, boolean gone, boolean player, boolean riding, boolean held, boolean boss, double maxHealth,
                         double width, double height, boolean othersAnimal, double distance, boolean sight) {
    }

    /** {@code reach}: the wearer's Reach; {@code tamedOthers}: the Server Config. */
    public record Rules(double reach, boolean tamedOthers) {
    }

    /** Lower-case names are the refusal keys ({@code tentaworks.refusal.grab.<name>}). */
    public enum Verdict {
        LIGHT, HEAVY, NOT_LIVING, GONE, PLAYER, RIDING, HELD, BOSS, OWNED, TOO_FAR, NO_SIGHT, HANDS_FULL, TWO_HANDS;

        public boolean ok() {
            return this == LIGHT || this == HEAVY;
        }

        /** Actuators it takes: 1 or 2, 0 when refused. */
        public int actuators() {
            return this == HEAVY ? 2 : this == LIGHT ? 1 : 0;
        }
    }

    private GrabRule() {
    }

    public static Verdict check(Target t, Rules r) {
        if (t.gone()) return Verdict.GONE;
        if (t.player()) return Verdict.PLAYER;
        if (!t.living()) return Verdict.NOT_LIVING;
        if (t.boss() || t.maxHealth() > BOSS_HEALTH) return Verdict.BOSS;
        if (t.riding()) return Verdict.RIDING;
        if (t.held()) return Verdict.HELD;
        if (t.othersAnimal() && !r.tamedOthers()) return Verdict.OWNED;
        if (t.distance() > r.reach() + SLACK) return Verdict.TOO_FAR;
        if (!t.sight()) return Verdict.NO_SIGHT;
        return light(t.width(), t.height()) ? Verdict.LIGHT : Verdict.HEAVY;
    }

    /**
     * Which Actuator takes a target needing {@code actuators} (1 light, 2 heavy or a Ship) with these hands: a light one the
     * off-hand Actuator when the off hand is empty, else the main-hand one when the main hand is empty; a heavy one both, only
     * with both hands empty. NONE: refused for {@link #hands}.
     */
    public static HoldArm arm(int actuators, boolean mainEmpty, boolean offEmpty) {
        if (actuators >= 2) return mainEmpty && offEmpty ? HoldArm.BOTH : HoldArm.NONE;
        if (offEmpty) return HoldArm.OFF;
        return mainEmpty ? HoldArm.MAIN : HoldArm.NONE;
    }

    /** An attack while holding Throws: the main hand is empty, or the Grab key is held; with an item in the main hand a plain attack is the item's. */
    public static boolean throwsOnAttack(boolean mainEmpty, boolean grabKeyDown) {
        return mainEmpty || grabKeyDown;
    }

    /** The refusal when {@link #arm} found no free Actuator: two hands needed (heavy, Ship) or hands full (light). */
    public static Verdict hands(int actuators) {
        return actuators >= 2 ? Verdict.TWO_HANDS : Verdict.HANDS_FULL;
    }

    /**
     * The protection check, the same a plain use of the target would get: {@code mayInteract} (spawn protection, world border,
     * and mods hooking it) first, then the loader's interaction event, posted only when that passed; {@code cancelled} is true
     * when a listener cancelled it.
     */
    public static boolean claimed(boolean mayInteract, BooleanSupplier cancelled) {
        return !mayInteract || cancelled.getAsBoolean();
    }

    public static boolean light(double width, double height) {
        return width <= LIGHT_WIDTH + EPS && height <= LIGHT_HEIGHT + EPS && width * width * height <= LIGHT_BULK + EPS;
    }
}
