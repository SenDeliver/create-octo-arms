package dev.tentaworks.rig;

import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import com.simibubi.create.content.equipment.zapper.ZapperItem;
import dev.tentaworks.compat.BetterCombat;
import dev.tentaworks.registry.TentaworksItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;

/**
 * Items an Actuator never carries: they stay in the wearer's own hand, because Create, vanilla or another mod draws and
 * animates them there itself (a Potato Cannon, a Zapper, a map, the item tag {@code tentaworks:own_hand}, a Better Combat weapon).
 */
public final class OwnHand {
    private OwnHand() {
    }

    public static boolean keeps(ItemStack stack) {
        return keeps(stack, BetterCombat.weapon(stack));
    }

    public static boolean keeps(ItemStack stack, boolean betterCombatWeapon) {
        return betterCombatWeapon || stack.getItem() instanceof MapItem || stack.getItem() instanceof PotatoCannonItem || stack.getItem() instanceof ZapperItem
                || stack.is(TentaworksItems.OWN_HAND);
    }
}
