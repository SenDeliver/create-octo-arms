package dev.tentaworks.network;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.Actuators;
import dev.tentaworks.rig.FarUse;
import dev.tentaworks.rig.Grab;
import dev.tentaworks.rig.WornRig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Wearer → server, the Furl key: Furl ({@code furled}) or unfurl the Actuators of the worn Rig. The state lives on the Rig item,
 * changed in place like the Rig Settings, so it is the server's, every watcher sees it and it survives a relog. Furling sets a held
 * target down first, by the normal release.
 */
public record ActuatorFurlPacket(boolean furled) implements CustomPacketPayload {
    public static final Type<ActuatorFurlPacket> TYPE = new Type<>(TentaworksIds.id(TentaworksIds.FURL));
    public static final StreamCodec<ByteBuf, ActuatorFurlPacket> STREAM_CODEC = ByteBufCodecs.BOOL.map(ActuatorFurlPacket::new, ActuatorFurlPacket::furled);
    public static final String FURLED = "tentaworks.actuators.furled", UNFURLED = "tentaworks.actuators.unfurled";
    public static final String KEY = "key." + TentaworksIds.MOD_ID + "." + TentaworksIds.FURL;

    public static void handle(ServerPlayer player, ActuatorFurlPacket packet) {
        ItemStack rig = WornRig.of(player).stack();
        if (!Actuators.fitted(rig) || player.isSpectator() || Actuators.furled(rig) == packet.furled) return;
        if (packet.furled) Grab.release(player, "furled", null);
        Actuators.furl(rig, packet.furled);
        FarUse.tick(player);
        player.displayClientMessage(packet.furled ? Component.translatable(FURLED, Component.keybind(KEY)) : Component.translatable(UNFURLED), true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
