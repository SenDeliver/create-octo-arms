package dev.tentaworks.rig;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.config.TentaworksConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The Rig's own enchantments (data-driven, {@code data/tentaworks/enchantment}). Strength per level comes from the
 * Server Config. Levels above an enchantment's max level count as the max level.
 */
public final class RigEnchants {
    public static final ResourceKey<Enchantment> FAR_REACH = key(TentaworksIds.FAR_REACH);
    public static final ResourceKey<Enchantment> THRIFT = key(TentaworksIds.THRIFT);
    public static final ResourceKey<Enchantment> SWIFT = key(TentaworksIds.SWIFT);
    /** Thrift can never cut Air use by more than this, whatever the config and level. */
    private static final double MAX_THRIFT = 0.9;

    private RigEnchants() {
    }

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT, TentaworksIds.id(path));
    }

    /** Side-safe: the holder comes from the stack itself, so client and server stacks resolve alike. */
    public static int level(ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty() || !stack.isEnchanted()) return 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> e : stack.getTagEnchantments().entrySet()) {
            Holder<Enchantment> holder = e.getKey();
            if (holder.is(key)) return Math.min(EnchantmentHelper.getItemEnchantmentLevel(holder, stack), holder.value().getMaxLevel());
        }
        return 0;
    }

    /** Extra Reach in blocks. */
    public static double farReach(ItemStack rig) {
        return level(rig, FAR_REACH) * TentaworksConfig.value(TentaworksConfig.ENCHANT_FAR_REACH);
    }

    /** Air use multiplier, 1 without Thrift. */
    public static double thrift(ItemStack rig) {
        return 1.0 - Math.min(MAX_THRIFT, level(rig, THRIFT) * TentaworksConfig.value(TentaworksConfig.ENCHANT_THRIFT));
    }

    /** Speed and top Speed multiplier, 1 without Swift. */
    public static double swift(ItemStack rig) {
        return 1.0 + level(rig, SWIFT) * TentaworksConfig.value(TentaworksConfig.ENCHANT_SWIFT);
    }
}
