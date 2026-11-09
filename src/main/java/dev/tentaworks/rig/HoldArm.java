package dev.tentaworks.rig;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * Which Actuator holds a Grab (CONTEXT: Grab). Each Actuator is a hand: the main-hand Actuator sits on the player's main-arm
 * side ({@link Player#getMainArm()}), the off-hand one on the other. Wire order is frozen: new values go at the end.
 */
public enum HoldArm {
    NONE, MAIN, OFF, BOTH;

    public boolean holding() {
        return this != NONE;
    }

    /** This hand's Actuator is busy holding. */
    public boolean uses(InteractionHand hand) {
        return this == BOTH || this == (hand == InteractionHand.MAIN_HAND ? MAIN : OFF);
    }

    /** The side of the body the hold hangs on: the holding Actuator's, the off-hand side for both. */
    public HumanoidArm side(HumanoidArm mainArm) {
        return this == MAIN ? mainArm : mainArm.getOpposite();
    }

    /** The Actuator on {@code side} of a player whose main arm is {@code mainArm}: MAIN or OFF. */
    public static HoldArm of(HumanoidArm side, HumanoidArm mainArm) {
        return side == mainArm ? MAIN : OFF;
    }

    /** The body side of {@code hand}'s Actuator. */
    public static HumanoidArm side(InteractionHand hand, HumanoidArm mainArm) {
        return hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
    }

    public static HoldArm byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : NONE;
    }
}
