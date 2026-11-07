package dev.tentaworks.rig;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Where a Rig sits for the Rig Settings screen and packet: either hand, the chest slot or the Curios Back Slot. The order is the wire id. */
public enum RigSlot {
    MAINHAND, OFFHAND, CHEST, BACK;

    public ItemStack stack(Player player) {
        return switch (this) {
            case MAINHAND -> player.getItemBySlot(EquipmentSlot.MAINHAND);
            case OFFHAND -> player.getItemBySlot(EquipmentSlot.OFFHAND);
            case CHEST -> player.getItemBySlot(EquipmentSlot.CHEST);
            case BACK -> WornRig.backRig(player);
        };
    }

    public static RigSlot of(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? MAINHAND : OFFHAND;
    }

    public static RigSlot byId(int id) {
        RigSlot[] all = values();
        return id >= 0 && id < all.length ? all[id] : MAINHAND;
    }

    public static RigSlot byName(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }

    public String getName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
