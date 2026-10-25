package dev.tentaworks.rig;

import com.simibubi.create.AllEnchantments;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.armor.CapacityEnchantment.ICapacityEnchantable;
import dev.tentaworks.registry.TentaworksItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.ForgeRegistries;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rig enchantments match the 1.21.1 data files (data/tentaworks/enchantment/*.json and the enchantment tags): Rig items
 * only, same levels, costs, weights and anvil costs, in the enchanting table, not treasure, traded by villagers and on random
 * loot (1.21.1 vanilla {@code tradeable} and {@code on_random_loot} include {@code #non_treasure}). Create's Capacity still
 * applies to a Rig. Runs in the dev server (fmlTest).
 */
class RigEnchantsTest {
    private static Enchantment[] ours() {
        return new Enchantment[]{RigEnchants.FAR_REACH.get(), RigEnchants.THRIFT.get(), RigEnchants.SWIFT.get(), RigEnchants.SILENT_STEP.get()};
    }

    private static Item[] rigs() {
        return new Item[]{TentaworksItems.COPPER_TENTACLE_RIG.get(), TentaworksItems.NETHERITE_TENTACLE_RIG.get()};
    }

    private static Item[] others() {
        return new Item[]{AllItems.COPPER_BACKTANK.get(), AllItems.NETHERITE_BACKTANK.get(), Items.DIAMOND_CHESTPLATE, Items.ELYTRA, Items.STICK};
    }

    /** At some enchanting power the table can reach (1..50 with bonuses), not a treasure roll. */
    private static boolean offered(ItemStack stack, Enchantment e) {
        for (int power = 1; power <= 50; power++) {
            List<EnchantmentInstance> offers = EnchantmentHelper.getAvailableEnchantmentResults(power, stack, false);
            if (offers.stream().anyMatch(o -> o.enchantment == e)) return true;
        }
        return false;
    }

    @Test
    void frozenIds() {
        assertEquals("tentaworks:far_reach", ForgeRegistries.ENCHANTMENTS.getKey(RigEnchants.FAR_REACH.get()).toString());
        assertEquals("tentaworks:thrift", ForgeRegistries.ENCHANTMENTS.getKey(RigEnchants.THRIFT.get()).toString());
        assertEquals("tentaworks:swift", ForgeRegistries.ENCHANTMENTS.getKey(RigEnchants.SWIFT.get()).toString());
        assertEquals("tentaworks:silent_step", ForgeRegistries.ENCHANTMENTS.getKey(RigEnchants.SILENT_STEP.get()).toString());
        assertEquals(1, RigEnchants.SILENT_STEP.get().getMaxLevel());
        assertEquals("enchantment.tentaworks.far_reach", RigEnchants.FAR_REACH.get().getDescriptionId());
    }

    @Test
    void categoryAcceptsRigItemsOnly() {
        for (Enchantment e : ours()) {
            for (Item rig : rigs()) {
                assertTrue(e.category.canEnchant(rig), e + " on " + rig);
                assertTrue(e.canEnchant(new ItemStack(rig)), e + " anvil on " + rig);
            }
            for (Item other : others()) {
                assertFalse(e.category.canEnchant(other), e + " on " + other);
                assertFalse(e.canEnchant(new ItemStack(other)), e + " anvil on " + other);
            }
        }
    }

    @Test
    void enchantingTable() {
        for (Enchantment e : ours()) {
            for (Item rig : rigs()) assertTrue(offered(new ItemStack(rig), e), e + " at the table for " + rig);
            assertTrue(offered(new ItemStack(Items.BOOK), e), e + " at the table for a book");
            for (Item other : others()) assertFalse(offered(new ItemStack(other), e), e + " at the table for " + other);
        }
    }

    @Test
    void levelsAndCosts() {
        assertEquals(3, RigEnchants.FAR_REACH.get().getMaxLevel());
        assertEquals(3, RigEnchants.THRIFT.get().getMaxLevel());
        assertEquals(2, RigEnchants.SWIFT.get().getMaxLevel());
        for (Enchantment e : ours()) assertEquals(1, e.getMinLevel());
        int[][] farReach = {{15, 65}, {27, 77}, {39, 89}};
        int[][] thrift = {{5, 55}, {15, 65}, {25, 75}};
        int[][] swift = {{15, 65}, {30, 80}};
        int[][] silentStep = {{20, 70}};
        int[][][] costs = {farReach, thrift, swift, silentStep};
        Enchantment[] all = ours();
        for (int i = 0; i < all.length; i++)
            for (int lvl = 1; lvl <= costs[i].length; lvl++) {
                assertEquals(costs[i][lvl - 1][0], all[i].getMinCost(lvl), all[i] + " min cost " + lvl);
                assertEquals(costs[i][lvl - 1][1], all[i].getMaxCost(lvl), all[i] + " max cost " + lvl);
            }
    }

    /** On 1.20.1 the rarity is both the weight and the anvil cost (Rarity COMMON..VERY_RARE = cost 1, 2, 4, 8). */
    @Test
    void weightsAndAnvilCosts() {
        assertEquals(Enchantment.Rarity.RARE, RigEnchants.FAR_REACH.get().getRarity());
        assertEquals(Enchantment.Rarity.UNCOMMON, RigEnchants.THRIFT.get().getRarity());
        assertEquals(Enchantment.Rarity.RARE, RigEnchants.SWIFT.get().getRarity());
        assertEquals(Enchantment.Rarity.RARE, RigEnchants.SILENT_STEP.get().getRarity());
        assertEquals(2, RigEnchants.FAR_REACH.get().getRarity().getWeight());
        assertEquals(5, RigEnchants.THRIFT.get().getRarity().getWeight());
        assertEquals(2, RigEnchants.SWIFT.get().getRarity().getWeight());
    }

    @Test
    void flags() {
        for (Enchantment e : ours()) {
            assertFalse(e.isTreasureOnly(), e + " treasure");
            assertFalse(e.isCurse(), e + " curse");
            assertTrue(e.isTradeable(), e + " tradeable");
            assertTrue(e.isDiscoverable(), e + " discoverable");
            assertTrue(e.isAllowedOnBooks(), e + " on books");
        }
    }

    @Test
    void levelCountsAtMostMax() {
        ItemStack rig = new ItemStack(TentaworksItems.COPPER_TENTACLE_RIG.get());
        assertEquals(0, RigEnchants.level(rig, RigEnchants.FAR_REACH));
        rig.enchant(RigEnchants.FAR_REACH.get(), 5);
        rig.enchant(RigEnchants.SWIFT.get(), 1);
        assertEquals(3, RigEnchants.level(rig, RigEnchants.FAR_REACH));
        assertEquals(1, RigEnchants.level(rig, RigEnchants.SWIFT));
        assertEquals(0, RigEnchants.level(rig, RigEnchants.THRIFT));
        assertEquals(0, RigEnchants.level(ItemStack.EMPTY, RigEnchants.FAR_REACH));
    }

    @Test
    void capacityStillApplies() {
        Enchantment capacity = AllEnchantments.CAPACITY.get();
        for (Item rig : rigs()) {
            assertInstanceOf(ICapacityEnchantable.class, rig);
            assertTrue(capacity.canEnchant(new ItemStack(rig)), "Capacity anvil on " + rig);
            assertTrue(offered(new ItemStack(rig), capacity), "Capacity at the table for " + rig);
        }
    }
}
