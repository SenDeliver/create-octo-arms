package dev.tentaworks.rig;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * How far an Actuator acts (CONTEXT: Reach): exactly as far as a Tentacle could Grip, a point within Reach of the Rig body
 * ({@link Grips#body}, the point the Grip rule measures from). The crosshair picks from the eye, which sits up to
 * {@value #EYE_OFFSET} above the body, so the picked ranges are lifted to Reach + {@value #EYE_OFFSET} ({@link #pickRange}) and
 * a pick is then accepted by its distance from the body. Client (crosshair, HUD, Grab key) and server (Far Use gates, Grab)
 * use this one rule; the server adds slack for its one-tick-old view.
 */
public final class ActuatorReach {
    /** A standing player's eye above its box centre (1.62 − 1.8 / 2), the largest of any pose. */
    public static final double EYE_OFFSET = 0.72;

    private ActuatorReach() {
    }

    /** The range from the eye a pick needs to cover every point within {@code reach} of the body. */
    public static double pickRange(double reach) {
        return reach + EYE_OFFSET;
    }

    /** The body for an eye at {@code eye} (a partial-tick eye too): the box centre below it. */
    public static Vec3 bodyFromEye(Player player, Vec3 eye) {
        return eye.subtract(0, player.getEyeHeight() - player.getBbHeight() / 2, 0);
    }

    public static boolean within(Vec3 body, Vec3 point, double reach) {
        return reach > 0 && body.distanceToSqr(point) <= reach * reach;
    }

    /** Some point of {@code box} lies within {@code reach} of the body. */
    public static boolean within(Vec3 body, AABB box, double reach) {
        return reach > 0 && box.distanceToSqr(body) <= reach * reach;
    }

    /** {@code point} is within this player's Actuator Reach (0 without a worn Rig). */
    public static boolean reaches(Player player, Vec3 point) {
        return within(Grips.body(player), point, TentacleRigItem.reach(player));
    }

    /** Some point of {@code box} is within this player's Actuator Reach. */
    public static boolean reaches(Player player, AABB box) {
        return within(Grips.body(player), box, TentacleRigItem.reach(player));
    }

    /**
     * A crosshair hit at {@code point} is one the hand or the Actuator can act on: within {@code base} of the eye (the vanilla
     * range without the lift, the hand's own) or within {@code reach} of the body.
     */
    public static boolean accepts(Vec3 eye, Vec3 body, Vec3 point, double base, double reach) {
        return eye.distanceToSqr(point) <= base * base || within(body, point, reach);
    }
}
