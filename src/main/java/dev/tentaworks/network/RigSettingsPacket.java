package dev.tentaworks.network;

import dev.tentaworks.rig.RigSettings;
import dev.tentaworks.rig.TentacleRigItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Rig Settings screen closed: write the settings to the Rig in that slot. The server clamps every value to its config.
 */
public record RigSettingsPacket(EquipmentSlot slot, RigSettings settings) {
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(switch (slot) {
            case OFFHAND -> 1;
            case CHEST -> 2;
            default -> 0;
        });
        settings.write(buf);
    }

    public static RigSettingsPacket read(FriendlyByteBuf buf) {
        EquipmentSlot slot = switch (buf.readVarInt()) {
            case 1 -> EquipmentSlot.OFFHAND;
            case 2 -> EquipmentSlot.CHEST;
            default -> EquipmentSlot.MAINHAND;
        };
        return new RigSettingsPacket(slot, RigSettings.read(buf));
    }

    public static void handle(ServerPlayer player, RigSettingsPacket packet) {
        ItemStack stack = player.getItemBySlot(packet.slot);
        if (!(stack.getItem() instanceof TentacleRigItem rig)) return;
        RigSettings.set(stack, packet.settings.clamped(rig.reach(stack)));
    }
}
