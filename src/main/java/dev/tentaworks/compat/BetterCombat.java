package dev.tentaworks.compat;

import dev.tentaworks.compat.bettercombat.BetterCombatWeapons;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.function.Predicate;

/**
 * The only door to Better Combat. Its weapons stay in the wearer's own hand: Better Combat owns their swing, hit and range.
 * Without Better Combat nothing is a Better Combat weapon; the implementation is loaded only when Better Combat is.
 */
public final class BetterCombat {
    private BetterCombat() {
    }

    /** The stack is a weapon Better Combat swings (it has Better Combat weapon attributes). */
    public static boolean weapon(ItemStack stack) {
        return !stack.isEmpty() && Holder.WEAPON.test(stack);
    }

    private static final class Holder {
        private static final Predicate<ItemStack> WEAPON = ModList.get() != null && ModList.get().isLoaded("bettercombat") ? BetterCombatWeapons.create() : s -> false;
    }
}
