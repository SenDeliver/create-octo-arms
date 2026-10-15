package dev.tentaworks.rig;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.function.Predicate;

/**
 * Sneaking with the Rig out: Shift moves the wearer down and still counts as sneaking for use and interaction (sneak + use
 * on Create blocks), but the wearer does not crouch, so the camera and hitbox stay put and vanilla sneak movement stays off.
 */
public final class RigSneak {
    private static Predicate<Player> clientDeployed = p -> false;

    private RigSneak() {
    }

    public static void clientSide(Predicate<Player> deployed) {
        clientDeployed = deployed;
    }

    /** The Rig is out (any state but Stowed), as this side knows it. */
    public static boolean deployed(Player player) {
        if (player instanceof ServerPlayer sp) return RigServer.state(sp) != RigState.STOWED;
        return player.level().isClientSide() && clientDeployed.test(player);
    }
}
