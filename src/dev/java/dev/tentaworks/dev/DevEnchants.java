package dev.tentaworks.dev;

import dev.tentaworks.rig.RigSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dev only. Enchanting table offers for the item in the main hand, computed by vanilla's own EnchantmentMenu
 * (a throwaway menu at a real table, so bookshelves and Forge events count) for many player enchantment seeds.
 * ENCHAVAIL: every enchantment the table can pick at any power 1..50 (deterministic). ENCHSTAT: clue counts per slot.
 * ENCHSEED: a seed whose third offer shows the wanted enchantment, set on the player so the real table shows it.
 */
final class DevEnchants {
    private static final Logger LOG = LoggerFactory.getLogger("tentaworks-dev");
    private static Field seedField;

    private DevEnchants() {
    }

    static String describe(ItemStack st) {
        return String.format("item=%s ench=%s stored=%s damage=%d/%d air=%d settings=%s", itemId(st),
                list(st.getEnchantmentTags()), list(EnchantedBookItem.getEnchantments(st)),
                st.getDamageValue(), st.getMaxDamage(), DevTools.air(st), RigSettings.stored(st));
    }

    private static String itemId(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
    }

    static String enchId(Enchantment e) {
        return String.valueOf(BuiltInRegistries.ENCHANTMENT.getKey(e));
    }

    private static String list(ListTag tags) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> x : EnchantmentHelper.deserializeEnchantments(tags).entrySet()) out.add(enchId(x.getKey()) + ":" + x.getValue());
        out.sort(null);
        return "[" + String.join(",", out) + "]";
    }

    private record Offers(int[] costs, int[] clue, int[] level) {
    }

    private static Offers offers(ServerPlayer p, BlockPos table, ItemStack item, int seed) {
        setSeed(p, seed);
        EnchantmentMenu m = new EnchantmentMenu(0, p.getInventory(), ContainerLevelAccess.create(p.level(), table));
        m.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
        m.getSlot(0).set(item.copy());
        return new Offers(m.costs.clone(), m.enchantClue.clone(), m.levelClue.clone());
    }

    private static String name(int id) {
        Enchantment e = id < 0 ? null : BuiltInRegistries.ENCHANTMENT.byId(id);
        return e == null ? "none" : enchId(e);
    }

    static void stats(ServerPlayer p, BlockPos table, int seeds) {
        ItemStack item = p.getMainHandItem();
        String itemId = itemId(item);
        Map<String, Integer> avail = new TreeMap<>();
        for (int power = 1; power <= 50; power++)
            for (EnchantmentInstance in : EnchantmentHelper.getAvailableEnchantmentResults(power, item, false))
                avail.merge(enchId(in.enchantment), in.level, Math::max);
        LOG.info("ENCHAVAIL item={} {}", itemId, avail);
        List<Map<String, Integer>> clues = List.of(new TreeMap<>(), new TreeMap<>(), new TreeMap<>());
        int[] costSum = new int[3];
        int saved = p.getEnchantmentSeed();
        for (int s = 0; s < seeds; s++) {
            Offers o = offers(p, table, item, s);
            for (int i = 0; i < 3; i++) {
                costSum[i] += o.costs[i];
                clues.get(i).merge(name(o.clue[i]), 1, Integer::sum);
            }
        }
        setSeed(p, saved);
        for (int i = 0; i < 3; i++)
            LOG.info("ENCHSTAT item={} seeds={} slot={} avgcost={} clues={}", itemId, seeds, i + 1, String.format("%.1f", costSum[i] / (double) seeds), clues.get(i));
    }

    static int findSeed(ServerPlayer p, BlockPos table, ResourceLocation want) {
        ItemStack item = p.getMainHandItem();
        int saved = p.getEnchantmentSeed();
        for (int s = 0; s < 100000; s++) {
            Offers o = offers(p, table, item, s);
            if (name(o.clue[2]).equals(want.toString())) {
                LOG.info("ENCHSEED item={} seed={} costs={},{},{} clues={}:{},{}:{},{}:{}", itemId(item), s,
                        o.costs[0], o.costs[1], o.costs[2], name(o.clue[0]), o.level[0], name(o.clue[1]), o.level[1], name(o.clue[2]), o.level[2]);
                return s;
            }
        }
        setSeed(p, saved);
        LOG.info("ENCHSEED item={} none for {}", itemId(item), want);
        return -1;
    }

    private static void setSeed(Player p, int seed) {
        try {
            if (seedField == null) {
                seedField = seedField();
                seedField.setAccessible(true);
            }
            seedField.setInt(p, seed);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field seedField() throws NoSuchFieldException {
        try {
            return Player.class.getDeclaredField("enchantmentSeed");
        } catch (NoSuchFieldException e) {
            return Player.class.getDeclaredField("f_36081_");
        }
    }
}
