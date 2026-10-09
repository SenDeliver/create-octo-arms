package dev.tentaworks.network;

import dev.tentaworks.client.RigClient;
import dev.tentaworks.rig.RigServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class TentaworksNetwork {
    private TentaworksNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("7");
        registrar.playToServer(RigMotionPacket.TYPE, RigMotionPacket.STREAM_CODEC,
                (p, ctx) -> RigServer.onMotion((ServerPlayer) ctx.player(), p));
        registrar.playToServer(RigSettingsPacket.TYPE, RigSettingsPacket.STREAM_CODEC,
                (p, ctx) -> RigSettingsPacket.handle((ServerPlayer) ctx.player(), p));
        registrar.playToClient(RigStatePacket.TYPE, RigStatePacket.STREAM_CODEC,
                (p, ctx) -> RigClient.onState(p));
    }
}
