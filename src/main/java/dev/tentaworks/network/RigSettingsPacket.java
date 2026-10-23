package dev.tentaworks.network;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.RigSettings;
import dev.tentaworks.rig.TentacleRigItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Rig Settings screen closed: write the settings to the Rig in that slot. The server clamps every value to its config.
 */
public record RigSettingsPacket(EquipmentSlot slot, RigSettings settings) implements CustomPacketPayload {
    public static final Type<RigSettingsPacket> TYPE = new Type<>(TentaworksIds.id(TentaworksIds.RIG_SETTINGS));
    private static final StreamCodec<ByteBuf, EquipmentSlot> SLOT_CODEC = ByteBufCodecs.idMapper(i -> switch (i) {
        case 1 -> EquipmentSlot.OFFHAND;
        case 2 -> EquipmentSlot.CHEST;
        default -> EquipmentSlot.MAINHAND;
    }, s -> switch (s) {
        case OFFHAND -> 1;
        case CHEST -> 2;
        default -> 0;
    });
    public static final StreamCodec<ByteBuf, RigSettingsPacket> STREAM_CODEC = StreamCodec.composite(
            SLOT_CODEC, RigSettingsPacket::slot,
            RigSettings.STREAM_CODEC, RigSettingsPacket::settings,
            RigSettingsPacket::new);

    public static void handle(ServerPlayer player, RigSettingsPacket packet) {
        ItemStack stack = player.getItemBySlot(packet.slot);
        if (!(stack.getItem() instanceof TentacleRigItem rig)) return;
        RigSettings.set(stack, packet.settings.clamped(rig.reach(stack)));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
