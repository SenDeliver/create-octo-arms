package dev.tentaworks.dev;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.tentaworks.registry.TentaworksDataComponents;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dev only. Enchanting table offers for the item in the main hand, computed by vanilla's own EnchantmentMenu
 * (a throwaway menu at a real table, so bookshelves and NeoForge events count) for many player enchantment seeds.
 * ENCHAVAIL: every enchantment the table can pick at any power 1..50 (deterministic). ENCHSTAT: clue counts per slot.
 * ENCHSEED: a seed whose third offer shows the wanted enchantment, set on the player so the real table shows it.
 */
final class DevEnchants {
    private static final Logger LOG = LoggerFactory.getLogger("tentaworks-dev");
    private static Field seedField;

    private DevEnchants() {
    }

    static String describe(ItemStack st) {
        return String.format("item=%s ench=%s stored=%s damage=%d/%d air=%d settings=%s", st.getItemHolder().getRegisteredName(),
                list(st.getTagEnchantments()), list(st.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)),
                st.getDamageValue(), st.getMaxDamage(), BacktankUtil.getAir(st), st.get(TentaworksDataComponents.RIG_SETTINGS.get()));
    }

    private static String list(ItemEnchantments e) {
        List<String> out = new ArrayList<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> x : e.entrySet()) out.add(x.getKey().getRegisteredName() + ":" + x.getIntValue());
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

    private static String name(IdMap<Holder<Enchantment>> ids, int id) {
        Holder<Enchantment> h = id < 0 ? null : ids.byId(id);
        return h == null ? "none" : h.getRegisteredName();
    }

    static void stats(ServerPlayer p, BlockPos table, int seeds) {
        ItemStack item = p.getMainHandItem();
        String itemId = item.getItemHolder().getRegisteredName();
        Registry<Enchantment> reg = p.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Map<String, Integer> avail = new TreeMap<>();
        reg.getTag(EnchantmentTags.IN_ENCHANTING_TABLE).ifPresent(tag -> {
            for (int power = 1; power <= 50; power++)
                for (EnchantmentInstance in : EnchantmentHelper.getAvailableEnchantmentResults(power, item, tag.stream()))
                    avail.merge(in.enchantment.getRegisteredName(), in.level, Math::max);
        });
        LOG.info("ENCHAVAIL item={} {}", itemId, avail);
        IdMap<Holder<Enchantment>> ids = reg.asHolderIdMap();
        List<Map<String, Integer>> clues = List.of(new TreeMap<>(), new TreeMap<>(), new TreeMap<>());
        int[] costSum = new int[3];
        int saved = p.getEnchantmentSeed();
        for (int s = 0; s < seeds; s++) {
            Offers o = offers(p, table, item, s);
            for (int i = 0; i < 3; i++) {
                costSum[i] += o.costs[i];
                clues.get(i).merge(name(ids, o.clue[i]), 1, Integer::sum);
            }
        }
        setSeed(p, saved);
        for (int i = 0; i < 3; i++)
            LOG.info("ENCHSTAT item={} seeds={} slot={} avgcost={} clues={}", itemId, seeds, i + 1, String.format("%.1f", costSum[i] / (double) seeds), clues.get(i));
    }

    static int findSeed(ServerPlayer p, BlockPos table, ResourceLocation want) {
        ItemStack item = p.getMainHandItem();
        IdMap<Holder<Enchantment>> ids = p.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
        int saved = p.getEnchantmentSeed();
        for (int s = 0; s < 100000; s++) {
            Offers o = offers(p, table, item, s);
            if (name(ids, o.clue[2]).equals(want.toString())) {
                LOG.info("ENCHSEED item={} seed={} costs={},{},{} clues={}:{},{}:{},{}:{}", item.getItemHolder().getRegisteredName(), s,
                        o.costs[0], o.costs[1], o.costs[2], name(ids, o.clue[0]), o.level[0], name(ids, o.clue[1]), o.level[1], name(ids, o.clue[2]), o.level[2]);
                return s;
            }
        }
        setSeed(p, saved);
        LOG.info("ENCHSEED item={} none for {}", item.getItemHolder().getRegisteredName(), want);
        return -1;
    }

    private static void setSeed(Player p, int seed) {
        try {
            if (seedField == null) {
                seedField = Player.class.getDeclaredField("enchantmentSeed");
                seedField.setAccessible(true);
            }
            seedField.setInt(p, seed);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
