package dev.tentaworks.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.tentaworks.compat.curios.CuriosBackSlot;
import dev.tentaworks.registry.TentaworksItems;
import dev.tentaworks.rig.TentacleRigItem;
import dev.tentaworks.rig.WornRig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Dev source set only, Curios only. /tentaworks back set ITEM AIR (puts ITEM, or minecraft:empty, into every player's first back
 * slot as a command would: no equip rule, logs SBACKSET), back try TAG (SBACKTRY: would a fresh Copper Rig be accepted by the
 * back slot (Curios' own slot check, with the one-Rig rule) and by the chest slot ({@code canEquip})), back info TAG (SBACK:
 * the worn Rig's slot, the Back Slot stack's Air and damage, armor value, Create's Air sources), back mending DAMAGE (adds
 * Mending to the Back Slot Rig and sets its damage, logs SBACKMEND), back enchant ID LEVEL (adds that enchantment to the Back Slot Rig, logs SBACKENCH).
 */
final class DevCurios {
    private static final Logger LOG = LoggerFactory.getLogger("tentaworks-dev");

    private DevCurios() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("back")
                .then(Commands.literal("set").then(Commands.argument("item", ResourceLocationArgument.id()).then(Commands.argument("air", IntegerArgumentType.integer(0)).executes(c -> {
                    var id = ResourceLocationArgument.getId(c, "item");
                    int air = IntegerArgumentType.getInteger(c, "air");
                    ItemStack stack = id.getPath().equals("empty") ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(id));
                    if (stack.getItem() instanceof TentacleRigItem) stack.set(AllDataComponents.BACKTANK_AIR, air);
                    int n = 0;
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
                        IDynamicStackHandler h = back(p);
                        if (h == null || h.getSlots() == 0) continue;
                        h.setStackInSlot(0, stack.copy());
                        n++;
                    }
                    LOG.info("SBACKSET item={} air={} players={}", id, air, n);
                    return reply(c.getSource(), "back " + id + " " + n);
                }))))
                .then(Commands.literal("mending").then(Commands.argument("damage", IntegerArgumentType.integer(0)).executes(c -> {
                    int dmg = IntegerArgumentType.getInteger(c, "damage");
                    var mending = c.getSource().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
                    int n = 0;
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
                        IDynamicStackHandler h = back(p);
                        if (h == null || h.getSlots() == 0 || !(h.getStackInSlot(0).getItem() instanceof TentacleRigItem)) continue;
                        ItemStack rig = h.getStackInSlot(0).copy();
                        rig.enchant(mending, 1);
                        rig.setDamageValue(dmg);
                        h.setStackInSlot(0, rig);
                        n++;
                    }
                    LOG.info("SBACKMEND damage={} players={}", dmg, n);
                    return reply(c.getSource(), "back mending " + n);
                })))
                .then(Commands.literal("enchant").then(Commands.argument("id", ResourceLocationArgument.id()).then(Commands.argument("level", IntegerArgumentType.integer(1)).executes(c -> {
                    var id = ResourceLocationArgument.getId(c, "id");
                    int level = IntegerArgumentType.getInteger(c, "level");
                    var ench = c.getSource().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, id));
                    int n = 0;
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
                        IDynamicStackHandler h = back(p);
                        if (h == null || h.getSlots() == 0 || !(h.getStackInSlot(0).getItem() instanceof TentacleRigItem)) continue;
                        ItemStack rig = h.getStackInSlot(0).copy();
                        rig.enchant(ench, level);
                        h.setStackInSlot(0, rig);
                        n++;
                    }
                    LOG.info("SBACKENCH id={} level={} players={}", id, level, n);
                    return reply(c.getSource(), "back enchant " + n);
                }))))
                .then(Commands.literal("try").then(Commands.argument("tag", StringArgumentType.word()).executes(c -> {
                    String tag = StringArgumentType.getString(c, "tag");
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
                        ItemStack rig = new ItemStack(TentaworksItems.COPPER_TENTACLE_RIG.get());
                        IDynamicStackHandler h = back(p);
                        LOG.info("SBACKTRY {} {} backok={} chestok={}", tag, p.getName().getString(), h != null && h.getSlots() > 0 && h.isItemValid(0, rig),
                                rig.canEquip(EquipmentSlot.CHEST, p));
                    }
                    return reply(c.getSource(), "back try " + tag);
                })))
                .then(Commands.literal("info").then(Commands.argument("tag", StringArgumentType.word()).executes(c -> {
                    String tag = StringArgumentType.getString(c, "tag");
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) LOG.info("SBACK {} {}", tag, info(p));
                    return reply(c.getSource(), "back info " + tag);
                }))));
    }

    private static String info(ServerPlayer p) {
        WornRig w = WornRig.of(p);
        ItemStack back = WornRig.backRig(p);
        IDynamicStackHandler h = back(p);
        return String.format("%s worn=%s backitem=%s backair=%d backdmg=%d backmax=%d armor=%d chest=%s sources=%d slots=%d",
                p.getName().getString(), w.worn() ? w.slot().getName() : "-", back.isEmpty() ? "-" : back.getItemHolder().getRegisteredName(),
                back.isEmpty() ? -1 : BacktankUtil.getAir(back), back.getDamageValue(), back.getMaxDamage(), p.getArmorValue(),
                p.getItemBySlot(EquipmentSlot.CHEST).isEmpty() ? "-" : p.getItemBySlot(EquipmentSlot.CHEST).getItemHolder().getRegisteredName(),
                BacktankUtil.getAllWithAir(p).size(), h == null ? 0 : h.getSlots());
    }

    private static IDynamicStackHandler back(ServerPlayer p) {
        return CuriosApi.getCuriosInventory(p).flatMap(inv -> inv.getStacksHandler(CuriosBackSlot.SLOT)).map(s -> s.getStacks()).orElse(null);
    }

    private static int reply(CommandSourceStack source, String msg) {
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
