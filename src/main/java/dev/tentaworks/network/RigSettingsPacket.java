package dev.tentaworks.network;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.registry.TentaworksDataComponents;
import dev.tentaworks.rig.RigSettings;
import dev.tentaworks.rig.RigSlot;
import dev.tentaworks.rig.TentacleRigItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Rig Settings screen closed: write the settings to the Rig in that slot. The server clamps every value to its config.
 */
public record RigSettingsPacket(RigSlot slot, RigSettings settings) implements CustomPacketPayload {
    public static final Type<RigSettingsPacket> TYPE = new Type<>(TentaworksIds.id(TentaworksIds.RIG_SETTINGS));
    /** 0 main hand, 1 off hand, 2 chest slot, 3 Back Slot (registrar 9); unknown ids read as the main hand. */
    private static final StreamCodec<ByteBuf, RigSlot> SLOT_CODEC = ByteBufCodecs.idMapper(RigSlot::byId, RigSlot::ordinal);
    public static final StreamCodec<ByteBuf, RigSettingsPacket> STREAM_CODEC = StreamCodec.composite(
            SLOT_CODEC, RigSettingsPacket::slot,
            RigSettings.STREAM_CODEC, RigSettingsPacket::settings,
            RigSettingsPacket::new);

    public static void handle(ServerPlayer player, RigSettingsPacket packet) {
        ItemStack stack = packet.slot.stack(player);
        if (!(stack.getItem() instanceof TentacleRigItem rig)) return;
        stack.set(TentaworksDataComponents.RIG_SETTINGS.get(), packet.settings.clamped(rig.reach(stack)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
