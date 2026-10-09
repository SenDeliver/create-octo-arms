package dev.tentaworks.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInput;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.registry.TentaworksDataComponents;
import dev.tentaworks.registry.TentaworksItems;
import net.createmod.catnip.math.Pointing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Dev only, phase 5. /tentaworks recipes (RECIPE lines: recipe manager lookups and matches), items (ITEM lines: stack sizes),
 * assemblyline X Y Z / assemblycheck X Y Z (a real 2-loop Sequenced Assembly line: belt, Deployers with a Sticker and an
 * Andesite Casing, Presses; ASSEMBLY lines), crafters X Y Z copper|netherite AIR / craftercheck X Y Z (a powered 3x3
 * Mechanical Crafter grid fed through its item capability; CRAFTER lines), named TAG (SNAME: name, Air, damage of every Rig).
 */
final class DevRecipes {
    private static final Logger LOG = LoggerFactory.getLogger("tentaworks-dev");
    private static final int STATIONS = 6;
    private static final int SPACING = 3;
    private static final int BELT = 20;

    private DevRecipes() {
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tentaworks").requires(s -> s.hasPermission(2))
                .then(Commands.literal("recipes").executes(DevRecipes::recipes))
                .then(Commands.literal("items").executes(DevRecipes::items))
                .then(Commands.literal("named").then(Commands.argument("tag", StringArgumentType.word()).executes(DevRecipes::named)))
                .then(Commands.literal("assemblyline").then(Commands.argument("at", BlockPosArgument.blockPos()).executes(DevRecipes::assemblyLine)))
                .then(Commands.literal("assemblycheck").then(Commands.argument("at", BlockPosArgument.blockPos()).executes(DevRecipes::assemblyCheck)))
                .then(Commands.literal("crafters").then(Commands.argument("at", BlockPosArgument.blockPos())
                        .then(Commands.argument("tier", StringArgumentType.word()).then(Commands.argument("air", IntegerArgumentType.integer(0)).executes(DevRecipes::crafters)))))
                .then(Commands.literal("craftercheck").then(Commands.argument("at", BlockPosArgument.blockPos()).executes(DevRecipes::crafterCheck))));
    }

    private static int reply(CommandSourceStack source, String msg) {
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static String id(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    static String comps(ItemStack s) {
        return String.format(Locale.ROOT, "item=%s count=%d air=%s damage=%d/%d ench=%s settings=%s name=%s", id(s), s.getCount(),
                s.has(AllDataComponents.BACKTANK_AIR) ? String.valueOf(BacktankUtil.getAir(s)) : "none", s.getDamageValue(), s.getMaxDamage(),
                s.getTagEnchantments().size(), s.get(TentaworksDataComponents.RIG_SETTINGS.get()),
                s.has(DataComponents.CUSTOM_NAME) ? s.getHoverName().getString() : "none");
    }

    /** Create's own crafter path ({@link RecipeGridHandler#tryToApplyRecipe}); rows top to bottom, the backtank holds 700 Air. */
    @SuppressWarnings("unchecked")
    private static void craft(ServerLevel level, String name, ItemLike[][] rows, ItemLike expected) {
        RecipeGridHandler.GroupedItems items = new RecipeGridHandler.GroupedItems();
        Map<org.apache.commons.lang3.tuple.Pair<Integer, Integer>, ItemStack> grid;
        try {
            Field f = RecipeGridHandler.GroupedItems.class.getDeclaredField("grid");
            f.setAccessible(true);
            grid = (Map<org.apache.commons.lang3.tuple.Pair<Integer, Integer>, ItemStack>) f.get(items);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < 3; c++) {
                if (rows[r][c] == null) continue;
                ItemStack s = new ItemStack(rows[r][c]);
                if (s.getItem() instanceof com.simibubi.create.content.equipment.armor.BacktankItem) s.set(AllDataComponents.BACKTANK_AIR, 700);
                grid.put(org.apache.commons.lang3.tuple.Pair.of(c, rows.length - 1 - r), s);
            }
        items.calcStats();
        MechanicalCraftingInput input = MechanicalCraftingInput.of(items);
        String found = "none";
        for (RecipeHolder<?> h : level.getRecipeManager().getRecipes())
            if (h.value().getType() == AllRecipeTypes.MECHANICAL_CRAFTING.getType() && ((Recipe<CraftingInput>) h.value()).matches(input, level)) found = h.id().toString();
        ItemStack result = RecipeGridHandler.tryToApplyRecipe(level, items);
        if (result == null) result = ItemStack.EMPTY;
        boolean ok = expected == Items.AIR ? result.isEmpty() : result.is(expected.asItem()) && BacktankUtil.getAir(result) == 0;
        LOG.info("RECIPE {} {} id={} result: {}", ok ? "PASS" : "FAIL", name, found, comps(result));
    }

    private static int recipes(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        var rm = level.getRecipeManager();
        ItemLike t = TentaworksItems.TENTACLE.get(), p = AllItems.PRECISION_MECHANISM.get(), b = AllBlocks.BRASS_CASING.get(), c = AllItems.COPPER_SHEET.get();
        craft(level, "copper_rig", new ItemLike[][]{{t, p, t}, {b, AllItems.COPPER_BACKTANK.get(), b}, {t, c, t}}, TentaworksItems.COPPER_TENTACLE_RIG.get());
        craft(level, "netherite_rig", new ItemLike[][]{{t, p, t}, {b, AllItems.NETHERITE_BACKTANK.get(), b}, {t, c, t}}, TentaworksItems.NETHERITE_TENTACLE_RIG.get());
        craft(level, "copper_rig_flipped_none", new ItemLike[][]{{t, c, t}, {b, AllItems.COPPER_BACKTANK.get(), b}, {t, p, t}}, Items.AIR);
        craft(level, "rig_missing_tentacle_none", new ItemLike[][]{{t, p, null}, {b, AllItems.COPPER_BACKTANK.get(), b}, {t, c, t}}, Items.AIR);

        Optional<RecipeHolder<?>> seq = rm.byKey(TentaworksIds.id("sequenced_assembly/" + TentaworksIds.TENTACLE));
        if (seq.isPresent() && seq.get().value() instanceof SequencedAssemblyRecipe sa) {
            ItemStack out = sa.getResultItem(level.registryAccess());
            List<String> steps = sa.getSequence().stream().map(s -> BuiltInRegistries.RECIPE_TYPE.getKey(s.getRecipe().getType()).toString()).toList();
            boolean ok = out.is(TentaworksItems.TENTACLE.get()) && sa.getLoops() == 2 && steps.size() == 3
                    && sa.getIngredient().test(new ItemStack(AllBlocks.PISTON_EXTENSION_POLE.get()))
                    && sa.getTransitionalItem().is(TentaworksItems.INCOMPLETE_TENTACLE.get());
            LOG.info("RECIPE {} tentacle_sequence id={} loops={} steps={} transitional={} result={}", ok ? "PASS" : "FAIL", seq.get().id(),
                    sa.getLoops(), steps, id(sa.getTransitionalItem()), id(out));
        } else {
            LOG.info("RECIPE FAIL tentacle_sequence missing");
        }

        ItemStack base = new ItemStack(TentaworksItems.COPPER_TENTACLE_RIG.get());
        base.set(AllDataComponents.BACKTANK_AIR, 444);
        base.set(DataComponents.CUSTOM_NAME, Component.literal("Smith"));
        base.setDamageValue(30);
        SmithingRecipeInput in = new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), base, new ItemStack(Items.NETHERITE_INGOT));
        var smith = rm.getRecipeFor(RecipeType.SMITHING, in, level);
        ItemStack sr = smith.map(h -> h.value().assemble(in, level.registryAccess())).orElse(ItemStack.EMPTY);
        boolean sok = sr.is(TentaworksItems.NETHERITE_TENTACLE_RIG.get()) && BacktankUtil.getAir(sr) == 444 && sr.getDamageValue() == 30;
        LOG.info("RECIPE {} smithing id={} result: {}", sok ? "PASS" : "FAIL", smith.map(h -> h.id().toString()).orElse("none"), comps(sr));
        SmithingRecipeInput bad = new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(TentaworksItems.NETHERITE_TENTACLE_RIG.get()), new ItemStack(Items.NETHERITE_INGOT));
        LOG.info("RECIPE {} smithing_netherite_base_none matched={}", rm.getRecipeFor(RecipeType.SMITHING, bad, level).isEmpty() ? "PASS" : "FAIL",
                rm.getRecipeFor(RecipeType.SMITHING, bad, level).map(h -> h.id().toString()).orElse("none"));

        List<String> ours = rm.getRecipes().stream().filter(h -> h.id().getNamespace().equals(TentaworksIds.MOD_ID)).map(h -> h.id().toString()).sorted().toList();
        LOG.info("RECIPE count tentaworks={} {}", ours.size(), ours);
        var adv = level.getServer().getAdvancements().get(TentaworksIds.id("doc_rig"));
        LOG.info("RECIPE advancement doc_rig present={} parent={}", adv != null, adv == null ? "none" : adv.value().parent().map(Object::toString).orElse("none"));
        return reply(ctx.getSource(), "recipes");
    }

    private static int items(CommandContext<CommandSourceStack> ctx) {
        for (ItemLike i : new ItemLike[]{TentaworksItems.TENTACLE.get(), TentaworksItems.INCOMPLETE_TENTACLE.get()})
            LOG.info("ITEM {} maxStack={} class={}", BuiltInRegistries.ITEM.getKey(i.asItem()), new ItemStack(i).getMaxStackSize(), i.asItem().getClass().getSimpleName());
        return reply(ctx.getSource(), "items");
    }

    private static int named(CommandContext<CommandSourceStack> ctx) {
        String tag = StringArgumentType.getString(ctx, "tag");
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers())
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack st = p.getInventory().getItem(i);
                if (!st.isEmpty()) LOG.info("SNAME {} {} slot={} {}", tag, p.getName().getString(), i, comps(st));
            }
        return reply(ctx.getSource(), "named " + tag);
    }

    private static void power(ServerLevel level, BlockPos pos, int rpm) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof IRotate rot)) return;
        Direction.Axis axis = rot.getRotationAxis(state);
        for (Direction dir : Direction.values()) {
            if (dir.getAxis() != axis || !rot.hasShaftTowards(level, pos, state, dir)) continue;
            BlockPos m = pos.relative(dir);
            if (!level.getBlockState(m).isAir()) continue;
            level.setBlock(m, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, dir.getOpposite()), 3);
            BlockEntity be = level.getBlockEntity(m);
            if (be != null) {
                CompoundTag t = be.saveWithoutMetadata(level.registryAccess());
                t.putInt("ScrollValue", rpm);
                be.loadWithComponents(t, level.registryAccess());
                be.setChanged();
            }
            return;
        }
        LOG.info("ASSEMBLY no motor spot for {}", pos.toShortString());
    }

    private static void hold(ServerLevel level, BlockPos deployer, ItemStack stack) {
        BlockEntity be = level.getBlockEntity(deployer);
        if (be == null) return;
        CompoundTag t = be.saveWithoutMetadata(level.registryAccess());
        CompoundTag held = (CompoundTag) stack.save(level.registryAccess());
        held.putByte("Slot", (byte) 0);
        ListTag inv = new ListTag();
        inv.add(held);
        t.put("Inventory", inv);
        be.loadWithComponents(t, level.registryAccess());
        be.setChanged();
    }

    private static int heldCount(ServerLevel level, BlockPos deployer) {
        BlockEntity be = level.getBlockEntity(deployer);
        if (be == null) return -1;
        ListTag inv = be.saveWithoutMetadata(level.registryAccess()).getList("Inventory", 10);
        for (int i = 0; i < inv.size(); i++)
            if (inv.getCompound(i).getByte("Slot") == 0) return ItemStack.parseOptional(level.registryAccess(), inv.getCompound(i)).getCount();
        return 0;
    }

    private static BlockPos station(BlockPos o, int i) {
        return o.offset(2 + i * SPACING, 2, 0);
    }

    /** Belt along +X from the origin, a Depot at its end; stations repeat the sequence twice: Sticker Deployer, Press, Casing Deployer. */
    private static int assemblyLine(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos o = BlockPosArgument.getLoadedBlockPos(ctx, "at");
        BlockPos end = o.east(BELT - 1);
        for (int x = -1; x <= BELT + 1; x++) for (int y = 0; y <= 3; y++) for (int z = -2; z <= 2; z++) level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        BeltConnectorItem.createBelts(level, o, end);
        level.setBlock(end.east(), AllBlocks.DEPOT.getDefaultState(), 3);
        power(level, o, -64);
        for (int i = 0; i < STATIONS; i++) {
            BlockPos s = station(o, i);
            switch (i % 3) {
                case 1 -> level.setBlock(s, AllBlocks.MECHANICAL_PRESS.getDefaultState(), 3);
                default -> {
                    level.setBlock(s, AllBlocks.DEPLOYER.getDefaultState().setValue(BlockStateProperties.FACING, Direction.DOWN), 3);
                    hold(level, s, new ItemStack(i % 3 == 0 ? AllBlocks.STICKER.get() : AllBlocks.ANDESITE_CASING.get(), 64));
                }
            }
            power(level, s, 64);
        }
        ItemEntity item = new ItemEntity(level, o.getX() + 0.5, o.getY() + 1.0, o.getZ() + 0.5, new ItemStack(AllBlocks.PISTON_EXTENSION_POLE.get()));
        item.setDeltaMovement(0, 0, 0);
        level.addFreshEntity(item);
        LOG.info("ASSEMBLY built at {} belt={} stations={}", o.toShortString(), BELT, STATIONS);
        return reply(ctx.getSource(), "assemblyline");
    }

    private static int assemblyCheck(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos o = BlockPosArgument.getLoadedBlockPos(ctx, "at");
        List<String> found = new ArrayList<>();
        IItemHandler depot = level.getCapability(Capabilities.ItemHandler.BLOCK, o.east(BELT), Direction.UP);
        if (depot != null) for (int i = 0; i < depot.getSlots(); i++) {
            ItemStack s = depot.getStackInSlot(i);
            if (!s.isEmpty()) found.add("depot:" + id(s) + "x" + s.getCount() + progress(s));
        }
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(o).inflate(BELT + 2, 4, 4)))
            found.add("entity@" + e.blockPosition().toShortString().replace(" ", "") + ":" + id(e.getItem()) + progress(e.getItem()));
        if (level.getBlockEntity(o) instanceof BeltBlockEntity belt && belt.getControllerBE() != null && belt.getControllerBE().getInventory() != null)
            for (TransportedItemStack t : belt.getControllerBE().getInventory().getTransportedItems())
                found.add(String.format(Locale.ROOT, "belt@%.1f:%s%s", t.beltPosition, id(t.stack), progress(t.stack)));
        List<String> held = new ArrayList<>();
        for (int i = 0; i < STATIONS; i++) if (i % 3 != 1) held.add(i + ":" + heldCount(level, station(o, i)));
        boolean ok = found.stream().anyMatch(s -> s.startsWith("depot:" + TentaworksIds.MOD_ID + ":" + TentaworksIds.TENTACLE + "x"));
        LOG.info("ASSEMBLY {} items={} deployerHeld={}", ok ? "PASS" : "WAIT", found, held);
        return reply(ctx.getSource(), "assemblycheck");
    }

    private static String progress(ItemStack s) {
        var sa = s.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        return sa == null ? "" : String.format(Locale.ROOT, "(step=%d,progress=%.2f)", sa.step(), sa.progress());
    }

    private static BlockState crafter(Direction pointTo) {
        BlockState base = AllBlocks.MECHANICAL_CRAFTER.getDefaultState().setValue(MechanicalCrafterBlock.HORIZONTAL_FACING, Direction.SOUTH);
        for (Pointing p : Pointing.values()) {
            BlockState s = base.setValue(MechanicalCrafterBlock.POINTING, p);
            if (MechanicalCrafterBlock.getTargetDirection(s) == pointTo) return s;
        }
        throw new IllegalStateException("no pointing for " + pointTo);
    }

    /**
     * A 3x3 crafter wall in the XY plane facing south, origin = bottom-left. Columns flow down, the bottom corners flow
     * into the bottom centre, which outputs down into a chest. A cogwheel left of the middle row meshes with the grid.
     */
    private static int crafters(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos o = BlockPosArgument.getLoadedBlockPos(ctx, "at");
        boolean netherite = StringArgumentType.getString(ctx, "tier").equals("netherite");
        int air = IntegerArgumentType.getInteger(ctx, "air");
        for (int x = -3; x <= 4; x++) for (int y = -1; y <= 3; y++) for (int z = -2; z <= 2; z++) level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++) {
                Direction to = y > 0 ? Direction.DOWN : x == 0 ? Direction.EAST : x == 2 ? Direction.WEST : Direction.DOWN;
                level.setBlock(o.offset(x, y, 0), crafter(to), 3);
            }
        level.setBlock(o.offset(1, -1, 0), Blocks.CHEST.defaultBlockState(), 3);
        BlockPos cog = o.offset(-1, 1, 0);
        level.setBlock(cog, AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3);
        power(level, cog, 128);
        ItemStack tank = new ItemStack(netherite ? AllItems.NETHERITE_BACKTANK.get() : AllItems.COPPER_BACKTANK.get());
        tank.set(AllDataComponents.BACKTANK_AIR, air);
        ItemStack[][] rows = {
                {new ItemStack(TentaworksItems.TENTACLE.get()), new ItemStack(AllItems.COPPER_SHEET.get()), new ItemStack(TentaworksItems.TENTACLE.get())},
                {new ItemStack(AllBlocks.BRASS_CASING.get()), tank, new ItemStack(AllBlocks.BRASS_CASING.get())},
                {new ItemStack(TentaworksItems.TENTACLE.get()), new ItemStack(AllItems.PRECISION_MECHANISM.get()), new ItemStack(TentaworksItems.TENTACLE.get())}};
        PENDING.add(new Pending(level, o, rows, level.getGameTime() + 10));
        LOG.info("CRAFTER built at {} tier={} backtankAir={}", o.toShortString(), netherite ? "netherite" : "copper", air);
        return reply(ctx.getSource(), "crafters");
    }

    private record Pending(ServerLevel level, BlockPos o, ItemStack[][] rows, long at) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /** Runs a few ticks after the build so the grid is powered before the items go in (as a player would fill it). */
    static void tick() {
        for (Pending p : List.copyOf(PENDING)) {
            if (p.level.getGameTime() < p.at) continue;
            PENDING.remove(p);
            fill(p);
        }
    }

    private static void fill(Pending p) {
        MechanicalCrafterBlockEntity root = (MechanicalCrafterBlockEntity) p.level.getBlockEntity(p.o.offset(1, 0, 0));
        List<MechanicalCrafterBlockEntity> chain = root == null ? List.of() : RecipeGridHandler.getAllCraftersOfChain(root);
        List<String> speeds = new ArrayList<>();
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                if (p.level.getBlockEntity(p.o.offset(x, y, 0)) instanceof KineticBlockEntity k) speeds.add(String.format(Locale.ROOT, "%.0f", k.getSpeed()));
        LOG.info("CRAFTER chain={} speeds={}", chain == null ? -1 : chain.size(), speeds);
        List<String> left = new ArrayList<>();
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++) {
                IItemHandler h = p.level.getCapability(Capabilities.ItemHandler.BLOCK, p.o.offset(x, y, 0), Direction.SOUTH);
                ItemStack rest = h == null ? p.rows[y][x] : h.insertItem(0, p.rows[y][x].copy(), false);
                if (!rest.isEmpty()) left.add(x + "," + y + ":" + id(rest));
            }
        LOG.info("CRAFTER filled 9 slots, refused={}", left);
    }

    private static int crafterCheck(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos o = BlockPosArgument.getLoadedBlockPos(ctx, "at");
        List<String> out = new ArrayList<>();
        IItemHandler chest = level.getCapability(Capabilities.ItemHandler.BLOCK, o.offset(1, -1, 0), Direction.UP);
        if (chest != null) for (int i = 0; i < chest.getSlots(); i++) {
            ItemStack s = chest.getStackInSlot(i);
            if (!s.isEmpty()) out.add("chest " + comps(s));
        }
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(o).inflate(6)))
            out.add("entity " + comps(e.getItem()));
        List<String> grid = new ArrayList<>();
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++)
                if (level.getBlockEntity(o.offset(x, y, 0)) instanceof MechanicalCrafterBlockEntity c && !c.getInventory().getItem(0).isEmpty())
                    grid.add(x + "," + y + ":" + id(c.getInventory().getItem(0)));
        for (String s : out) LOG.info("CRAFTER out {}", s);
        LOG.info("CRAFTER check outputs={} gridItems={}", out.size(), grid);
        return reply(ctx.getSource(), "craftercheck");
    }
}
