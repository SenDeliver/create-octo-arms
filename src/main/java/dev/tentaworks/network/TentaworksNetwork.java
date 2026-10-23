package dev.tentaworks.network;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.client.RigClient;
import dev.tentaworks.rig.RigServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class TentaworksNetwork {
    private static final String PROTOCOL = "8";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(TentaworksIds.id("main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private TentaworksNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(RigMotionPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RigMotionPacket::write).decoder(RigMotionPacket::read)
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    if (player != null) RigServer.onMotion(player, p);
                })
                .add();
        CHANNEL.messageBuilder(RigSettingsPacket.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RigSettingsPacket::write).decoder(RigSettingsPacket::read)
                .consumerMainThread((p, ctx) -> {
                    ServerPlayer player = ctx.get().getSender();
                    if (player != null) RigSettingsPacket.handle(player, p);
                })
                .add();
        CHANNEL.messageBuilder(RigStatePacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RigStatePacket::write).decoder(RigStatePacket::read)
                .consumerMainThread((p, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RigClient.onState(p)))
                .add();
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Players tracking the entity, not the entity itself (same recipients as the 1.21 sendToPlayersTrackingEntity). */
    public static void sendToPlayersTrackingEntity(Entity entity, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }
}
