package dev.tentaworks.network;

import dev.tentaworks.rig.RigSettings;
import dev.tentaworks.rig.RigSlot;
import dev.tentaworks.rig.TentacleRigItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Rig Settings screen closed: write the settings to the Rig in that slot. The server clamps every value to its config.
 */
public record RigSettingsPacket(RigSlot slot, RigSettings settings) {
    /** Slot ids: 0 main hand, 1 off hand, 2 chest slot, 3 Back Slot (protocol 9); unknown ids read as the main hand. */
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(slot.ordinal());
        settings.write(buf);
    }

    public static RigSettingsPacket read(FriendlyByteBuf buf) {
        return new RigSettingsPacket(RigSlot.byId(buf.readVarInt()), RigSettings.read(buf));
    }

    public static void handle(ServerPlayer player, RigSettingsPacket packet) {
        ItemStack stack = packet.slot.stack(player);
        if (!(stack.getItem() instanceof TentacleRigItem rig)) return;
        RigSettings.set(stack, packet.settings.clamped(rig.reach(stack)));
    }
}
