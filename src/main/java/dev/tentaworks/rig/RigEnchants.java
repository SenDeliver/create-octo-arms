package dev.tentaworks.rig;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.config.TentaworksConfig;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Rig's own enchantments. Strength per level comes from the Server Config. Levels above an enchantment's max level
 * count as the max level. Costs, rarity (= weight and anvil cost) and max levels match the 1.21.1 data files.
 */
public final class RigEnchants {
    private static final DeferredRegister<Enchantment> REGISTER = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, TentaworksIds.MOD_ID);
    private static EnchantmentCategory category;

    public static final RegistryObject<Enchantment> FAR_REACH = REGISTER.register(TentaworksIds.FAR_REACH, FarReach::new);
    public static final RegistryObject<Enchantment> THRIFT = REGISTER.register(TentaworksIds.THRIFT, Thrift::new);
    public static final RegistryObject<Enchantment> SWIFT = REGISTER.register(TentaworksIds.SWIFT, Swift::new);
    /** Thrift can never cut Air use by more than this, whatever the config and level. */
    private static final double MAX_THRIFT = 0.9;

    private RigEnchants() {
    }

    public static void register(IEventBus modBus) {
        REGISTER.register(modBus);
    }

    public static boolean enchantable(Item item) {
        return item instanceof TentacleRigItem;
    }

    private static EnchantmentCategory category() {
        if (category == null) category = EnchantmentCategory.create("tentaworks_rig", RigEnchants::enchantable);
        return category;
    }

    public static int level(ItemStack stack, RegistryObject<Enchantment> enchantment) {
        if (stack.isEmpty() || !stack.isEnchanted()) return 0;
        Enchantment e = enchantment.get();
        return Math.min(stack.getEnchantmentLevel(e), e.getMaxLevel());
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

    /** min cost = minBase + perLevel * (level - 1), max cost = maxBase + perLevel * (level - 1). */
    public static class RigEnchantment extends Enchantment {
        private final int maxLevel, minBase, maxBase, perLevel;

        protected RigEnchantment(Rarity rarity, int maxLevel, int minBase, int maxBase, int perLevel) {
            super(rarity, category(), new EquipmentSlot[]{TentacleRigItem.SLOT});
            this.maxLevel = maxLevel;
            this.minBase = minBase;
            this.maxBase = maxBase;
            this.perLevel = perLevel;
        }

        @Override
        public int getMaxLevel() {
            return maxLevel;
        }

        @Override
        public int getMinCost(int level) {
            return minBase + perLevel * (level - 1);
        }

        @Override
        public int getMaxCost(int level) {
            return maxBase + perLevel * (level - 1);
        }
    }

    public static class FarReach extends RigEnchantment {
        public FarReach() {
            super(Rarity.RARE, 3, 15, 65, 12);
        }
    }

    public static class Thrift extends RigEnchantment {
        public Thrift() {
            super(Rarity.UNCOMMON, 3, 5, 55, 10);
        }
    }

    public static class Swift extends RigEnchantment {
        public Swift() {
            super(Rarity.RARE, 2, 15, 65, 15);
        }
    }
}
