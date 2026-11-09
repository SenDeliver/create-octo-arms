package dev.tentaworks.rig;

import dev.tentaworks.network.ActuatorStatePacket;
import dev.tentaworks.network.RigGrabPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Left Actuator's Grab and Throw, server side (adr/0007: the server is the authority). Every hold ends in
 * {@link #release}. This build holds nothing yet: every request is answered with an empty state.
 */
public final class Grab {
    private Grab() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) logout(p);
        });
    }

    public static void onPacket(ServerPlayer wearer, RigGrabPacket packet) {
        PacketDistributor.sendToPlayer(wearer, ActuatorStatePacket.empty(wearer.getId(), ""));
    }

    /**
     * Ends the wearer's hold, if any: the target is set down where it is ({@code velocity} null) or flung with {@code velocity}
     * (blocks per tick). {@code why} names the cause for logs and events. True when something was held.
     */
    public static boolean release(ServerPlayer wearer, String why, @Nullable Vec3 velocity) {
        return false;
    }

    public static boolean holding(ServerPlayer wearer) {
        return false;
    }

    /** The held target takes both Actuators (a heavy mob or a Ship). */
    public static boolean heavy(ServerPlayer wearer) {
        return false;
    }

    public static void logout(ServerPlayer wearer) {
        release(wearer, "logout", null);
    }

    /** A Grip on the Ship this wearer holds: it does not carry the wearer. */
    public static boolean onHeldShip(ServerPlayer wearer, Grip grip) {
        return false;
    }
}
