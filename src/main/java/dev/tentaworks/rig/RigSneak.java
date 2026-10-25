package dev.tentaworks.rig;

import net.minecraft.world.entity.player.Player;

/**
 * Sneaking with the Rig out: Shift moves the wearer down and still counts as sneaking for use and interaction (sneak + use
 * on Create blocks), but the wearer does not crouch, so the camera and hitbox stay put and vanilla sneak movement stays off.
 */
public final class RigSneak {
    private RigSneak() {
    }

    /** The Rig is out (any state but Stowed), as this side knows it. */
    public static boolean deployed(Player player) {
        return RigSide.deployed(player);
    }
}
