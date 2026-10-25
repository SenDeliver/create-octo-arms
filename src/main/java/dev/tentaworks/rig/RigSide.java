package dev.tentaworks.rig;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.function.Function;

/** A player's Rig state as this side knows it: the server's record for a ServerPlayer, the client's own or watched state else. */
public final class RigSide {
    private static Function<Player, RigState> clientState = p -> RigState.STOWED;

    private RigSide() {
    }

    public static void clientSide(Function<Player, RigState> state) {
        clientState = state;
    }

    public static RigState state(Player player) {
        if (player instanceof ServerPlayer sp) return RigServer.state(sp);
        return player.level().isClientSide() ? clientState.apply(player) : RigState.STOWED;
    }

    /** The Rig is out: any state but Stowed. */
    public static boolean deployed(Player player) {
        return state(player) != RigState.STOWED;
    }

    /** The Rig carries the wearer (Active, Setting Down or Crawl): the wearer is Held, not on the floor and not falling. */
    public static boolean carried(Player player) {
        return state(player).carries();
    }
}
