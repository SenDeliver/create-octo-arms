package dev.tentaworks.rig;

/**
 * A carried wearer's hands work as if they stood on the floor. Vanilla treats anyone not on the ground as airborne:
 * mining runs at a fifth of the speed and a sword never sweeps. Held by the Rig the wearer is as steady as standing, so
 * for those two checks "on the ground" also means "carried" (Falling stays airborne).
 */
public final class RigHands {
    private RigHands() {
    }

    public static boolean onGround(boolean onGround, RigState state) {
        return onGround || state.carries();
    }
}
