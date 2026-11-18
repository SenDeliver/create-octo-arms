package dev.tentaworks.compat.bettercombat;

import com.mojang.logging.LogUtils;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.function.Predicate;

/**
 * Better Combat (2.x, NeoForge 1.21.1): the only class that touches it. Loaded only when Better Combat is. Its weapon lookup
 * is not in its API package, so a Better Combat that moved it is logged once and then counts nothing as its weapon (the item
 * tag {@code tentaworks:own_hand} still keeps listed items in the own hand).
 */
public final class BetterCombatWeapons implements Predicate<ItemStack> {
    private static final Logger LOG = LogUtils.getLogger();
    private boolean broken;

    private BetterCombatWeapons() {
    }

    public static Predicate<ItemStack> create() {
        return new BetterCombatWeapons();
    }

    @Override
    public boolean test(ItemStack stack) {
        if (broken) return false;
        try {
            return WeaponRegistry.getAttributes(stack) != null;
        } catch (LinkageError | RuntimeException e) {
            broken = true;
            LOG.warn("Better Combat weapon lookup failed, its weapons are treated as plain items (use the tentaworks:own_hand tag): {}", e.toString());
            return false;
        }
    }
}
