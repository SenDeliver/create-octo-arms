package dev.tentaworks.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ServerChainConveyorHandler;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.compat.Frame;
import dev.tentaworks.compat.ShipFrames;
import dev.tentaworks.network.RigMotionPacket;
import dev.tentaworks.rig.Grip;
import dev.tentaworks.rig.TentacleGait;
import dev.tentaworks.rig.Tentacles;
import dev.tentaworks.rig.Grips;
import dev.tentaworks.registry.TentaworksDataComponents;
import dev.tentaworks.rig.RigEnchants;
import dev.tentaworks.rig.RigServer;
import dev.tentaworks.rig.TentacleRigItem;
import dev.tentaworks.rig.RigState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dev source set only, never in the release jar. /tentaworks trace on|off (STRACE per tick), pos TAG (one SPOS line),
 * floatfix on|off (negative control for the float kick), fake active|nan|held|climb TICKS (forged Active packets every tick: no Grips, NaN motion,
 * real Grips, real Grips with an upward motion, or fastN: real Grips with a level motion of N blocks per second), air N (sets the worn Rig's Air), fallfix on|off (negative control for the long-fall
 * float kick), rigs TAG (SRIG lines: Rig Settings component per slot),
 * inv TAG (SINV lines: every inventory item with enchantments, damage, Air), enchstats X Y Z SEEDS and enchseed X Y Z ENCHANTMENT
 * (enchanting table offers, see {@link DevEnchants}). Phase 8: fake downrise|throughX|edge (Setting Down claims with a rising motion; Grips on the far
 * (east) face of the wall column at x=X; Grips on a block at x=20047 whose outer neighbour is in unloaded chunk 1253 when only 20000 0 is forceloaded), burst N (each fake packet
 * sent N times per tick), netstats TAG (NETSTATS: motion packets handled, relays, server tick time), probe X Y Z (reads a block:
 * CHUNKLOAD control), fake slideN (real Grips, zero claimed motion, the server moves the player N blocks per second: speed check),
 * ghost FROM TO (air on the server only: a client ghost block), nbt X Y Z TAG (SNBT: a block entity's NBT), chain FROM TO (connects two Create chain conveyors). Logs CHUNKLOAD for chunks at x ≥ 1250. Logs FALL (LivingFallEvent distance) and HURT (damage taken) lines for fall-damage checks.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID)
public final class DevTools {
    private static final Logger LOG = LoggerFactory.getLogger("tentaworks-dev");
    private static boolean trace;
    private static String fakeKind = "";
    private static int fakeTicks;
    private static int burst = 1;
    private static long tickStart;
    private static int tickCount;
    private static long tickNanos;
    private static long maxTickNanos;

    private DevTools() {
    }

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        LOG.info("tentaworks dev tools active (dist={})", event.getServer().isDedicatedServer() ? "dedicated" : "integrated");
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        DevRecipes.register(event.getDispatcher());
        DevPonder.register(event.getDispatcher());
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("tentaworks").requires(s -> s.hasPermission(2));
        event.getDispatcher().register(root
                .then(Commands.literal("trace").then(Commands.argument("on", StringArgumentType.word()).executes(c -> {
                    trace = StringArgumentType.getString(c, "on").equals("on");
                    return reply(c.getSource(), "trace " + trace);
                })))
                .then(Commands.literal("floatfix").then(Commands.argument("on", StringArgumentType.word()).executes(c -> {
                    boolean on = StringArgumentType.getString(c, "on").equals("on");
                    RigServer.setFloatFix(on);
                    LOG.info("floatfix {}", on);
                    return reply(c.getSource(), "floatfix " + on);
                })))
                .then(Commands.literal("fallfix").then(Commands.argument("on", StringArgumentType.word()).executes(c -> {
                    boolean on = StringArgumentType.getString(c, "on").equals("on");
                    RigServer.setFallFix(on);
                    LOG.info("fallfix {}", on);
                    return reply(c.getSource(), "fallfix " + on);
                })))
                .then(Commands.literal("rigs").then(Commands.argument("tag", StringArgumentType.word()).executes(c -> {
                    String tag = StringArgumentType.getString(c, "tag");
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers())
                        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.CHEST}) {
                            ItemStack st = p.getItemBySlot(slot);
                            if (st.isEmpty()) continue;
                            LOG.info("SRIG {} {} slot={} item={} settings={}", tag, p.getName().getString(), slot.getName(),
                                    st.getItemHolder().getRegisteredName(), st.get(TentaworksDataComponents.RIG_SETTINGS.get()));
                        }
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
                        ItemStack worn = p.getItemBySlot(EquipmentSlot.CHEST);
                        if (worn.getItem() instanceof TentacleRigItem)
                            LOG.info("SREACH {} {} reach={} top={} thrift={} swift={}", tag, p.getName().getString(), String.format("%.2f", RigServer.reach(p)),
                                    String.format("%.2f", TentacleRigItem.topSpeed(worn)), String.format("%.3f", RigEnchants.thrift(worn)), String.format("%.3f", RigEnchants.swift(worn)));
                    }
                    return reply(c.getSource(), "rigs " + tag);
                })))
                .then(Commands.literal("fake").then(Commands.argument("kind", StringArgumentType.word()).then(Commands.argument("ticks", IntegerArgumentType.integer(1)).executes(c -> {
                    fakeKind = StringArgumentType.getString(c, "kind");
                    fakeTicks = IntegerArgumentType.getInteger(c, "ticks");
                    LOG.info("fake {} for {} ticks", fakeKind, fakeTicks);
                    return reply(c.getSource(), "fake " + fakeKind);
                }))))
                .then(Commands.literal("air").then(Commands.argument("n", IntegerArgumentType.integer(0)).executes(c -> {
                    int n = IntegerArgumentType.getInteger(c, "n");
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
                        ItemStack rig = p.getItemBySlot(EquipmentSlot.CHEST);
                        if (!rig.isEmpty()) rig.set(AllDataComponents.BACKTANK_AIR, n);
                    }
                    LOG.info("air set {}", n);
                    return reply(c.getSource(), "air " + n);
                })))
                .then(Commands.literal("inv").then(Commands.argument("tag", StringArgumentType.word()).executes(c -> {
                    String tag = StringArgumentType.getString(c, "tag");
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
                        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                            ItemStack st = p.getInventory().getItem(i);
                            if (!st.isEmpty()) LOG.info("SINV {} {} slot={} {}", tag, p.getName().getString(), i, DevEnchants.describe(st));
                        }
                        ItemStack carried = p.containerMenu.getCarried();
                        if (!carried.isEmpty()) LOG.info("SINV {} {} slot=carried {}", tag, p.getName().getString(), DevEnchants.describe(carried));
                    }
                    return reply(c.getSource(), "inv " + tag);
                })))
                .then(Commands.literal("enchstats").then(Commands.argument("table", BlockPosArgument.blockPos()).then(Commands.argument("seeds", IntegerArgumentType.integer(1)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    DevEnchants.stats(p, BlockPosArgument.getLoadedBlockPos(c, "table"), IntegerArgumentType.getInteger(c, "seeds"));
                    return reply(c.getSource(), "enchstats");
                }))))
                .then(Commands.literal("enchseed").then(Commands.argument("table", BlockPosArgument.blockPos()).then(Commands.argument("enchantment", ResourceLocationArgument.id()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    int seed = DevEnchants.findSeed(p, BlockPosArgument.getLoadedBlockPos(c, "table"), ResourceLocationArgument.getId(c, "enchantment"));
                    return reply(c.getSource(), "enchseed " + seed);
                }))))
                .then(Commands.literal("netstats").then(Commands.argument("tag", StringArgumentType.word()).executes(c -> {
                    long[] st = RigServer.stats();
                    LOG.info("NETSTATS {} processed={} relays={} ticks={} avg_tick_us={} max_tick_us={}", StringArgumentType.getString(c, "tag"), st[0], st[1],
                            tickCount, tickCount == 0 ? 0 : String.format("%.1f", tickNanos / 1000.0 / tickCount), String.format("%.1f", maxTickNanos / 1000.0));
                    tickCount = 0;
                    tickNanos = 0;
                    maxTickNanos = 0;
                    return reply(c.getSource(), "netstats");
                })))
                .then(Commands.literal("burst").then(Commands.argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                    burst = IntegerArgumentType.getInteger(c, "n");
                    LOG.info("burst {}", burst);
                    return reply(c.getSource(), "burst " + burst);
                })))
                .then(Commands.literal("probe").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                    var pos = BlockPosArgument.getBlockPos(c, "pos");
                    LOG.info("PROBE {} {}", pos.toShortString(), c.getSource().getLevel().getBlockState(pos));
                    return reply(c.getSource(), "probe");
                })))
                .then(Commands.literal("ghost").then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos()).executes(c -> {
                    var a = BlockPosArgument.getBlockPos(c, "from");
                    var b = BlockPosArgument.getBlockPos(c, "to");
                    int n = 0;
                    for (var pos : net.minecraft.core.BlockPos.betweenClosed(a, b)) {
                        c.getSource().getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 16 | 32);
                        n++;
                    }
                    LOG.info("GHOST {} blocks removed server-side only", n);
                    return reply(c.getSource(), "ghost " + n);
                }))))
                .then(Commands.literal("nbt").then(Commands.argument("pos", BlockPosArgument.blockPos()).then(Commands.argument("tag", StringArgumentType.word()).executes(c -> {
                    var pos = BlockPosArgument.getBlockPos(c, "pos");
                    var be = c.getSource().getLevel().getBlockEntity(pos);
                    LOG.info("SNBT {} gt={} {} {}", StringArgumentType.getString(c, "tag"), c.getSource().getLevel().getGameTime(), pos.toShortString(),
                            be == null ? "none" : be.saveWithoutMetadata(c.getSource().registryAccess()));
                    return reply(c.getSource(), "nbt");
                }))))
                .then(Commands.literal("chain").then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos()).executes(c -> {
                    var a = BlockPosArgument.getBlockPos(c, "from");
                    var b = BlockPosArgument.getBlockPos(c, "to");
                    var level = c.getSource().getLevel();
                    boolean ok = false;
                    if (level.getBlockEntity(a) instanceof ChainConveyorBlockEntity ca && level.getBlockEntity(b) instanceof ChainConveyorBlockEntity cb) {
                        ca.prepareStats();
                        cb.prepareStats();
                        cb.addConnectionTo(a);
                        ca.addConnectionTo(b);
                        ok = ca.connections.contains(b.subtract(a)) && cb.connections.contains(a.subtract(b));
                    }
                    LOG.info("CHAIN {} {} connected={}", a.toShortString(), b.toShortString(), ok);
                    return reply(c.getSource(), "chain " + ok);
                }))))
                .then(Commands.literal("pos").then(Commands.argument("tag", StringArgumentType.word()).executes(c -> {
                    String tag = StringArgumentType.getString(c, "tag");
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) LOG.info("SPOS {} {}", tag, line(p));
                    return reply(c.getSource(), "pos " + tag);
                }))));
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p)
            LOG.info("FALL gt={} {} distance={} multiplier={}", p.level().getGameTime(), p.getName().getString(),
                    String.format("%.3f", event.getDistance()), event.getDamageMultiplier());
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p)
            LOG.info("HURT gt={} {} source={} amount={}", p.level().getGameTime(), p.getName().getString(),
                    event.getSource().getMsgId(), String.format("%.1f", event.getNewDamage()));
    }

    /** Fake packets go in before the Rig handles this tick's packets, so they replace the real client's (as a cheating client's would). */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    static void onTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        tickStart = System.nanoTime();
        if (fakeTicks > 0) {
            fakeTicks--;
            boolean fast = fakeKind.startsWith("fast");
            Vec3 motion = fast ? new Vec3(Double.parseDouble(fakeKind.substring(4)) / 20.0, 0, 0) : switch (fakeKind) {
                case "nan" -> new Vec3(Double.NaN, 0, 0);
                case "climb", "downrise" -> new Vec3(0, 0.28, 0);
                default -> Vec3.ZERO;
            };
            for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                Tentacles tentacles = Tentacles.NONE;
                Vec3 body = Grips.body(p);
                boolean slide = fakeKind.startsWith("slide");
                if (slide) {
                    p.connection.teleport(p.getX() + Double.parseDouble(fakeKind.substring(5)) / 20.0, p.getY(), p.getZ(), p.getYRot(), p.getXRot());
                    body = Grips.body(p);
                }
                if (fast || slide || fakeKind.equals("held") || fakeKind.equals("climb") || fakeKind.equals("downrise")) {
                    double reach = RigServer.reach(p);
                    TentacleGait gait = new TentacleGait();
                    gait.tick(p.level(), Grips.faces(p.level(), body, reach + 1), body, p.yBodyRot, Vec3.ZERO, reach, 0, Double.NaN);
                    tentacles = gait.wire();
                } else if (fakeKind.startsWith("through") || fakeKind.equals("edge")) {
                    Grip[] g = new Grip[4];
                    if (fakeKind.equals("edge")) {
                        net.minecraft.core.BlockPos b = new net.minecraft.core.BlockPos(20047, -61, 0);
                        for (int i = 0; i < 4; i++) g[i] = new Grip(body, b.below(i), net.minecraft.core.Direction.EAST);
                    } else {
                        int x = Integer.parseInt(fakeKind.substring(7));
                        for (int i = 0; i < 4; i++) {
                            net.minecraft.core.BlockPos b = new net.minecraft.core.BlockPos(x, (int) Math.floor(p.getY()) + i % 2, (int) Math.floor(p.getZ()) + i / 2);
                            g[i] = new Grip(new Vec3(x + 1, b.getY() + 0.5, b.getZ() + 0.5), b, net.minecraft.core.Direction.EAST);
                        }
                    }
                    tentacles = new Tentacles(g, new byte[]{-1, -1, -1, -1});
                }
                for (int k = 0; k < burst; k++) RigServer.onMotion(p, new RigMotionPacket(fakeKind.equals("downrise") ? RigState.SETTING_DOWN : RigState.ACTIVE, motion, tentacles));
            }
        }
    }

    @SubscribeEvent
    static void onChunkLoad(net.minecraftforge.event.level.ChunkEvent.Load event) {
        if (!event.getLevel().isClientSide() && event.getChunk().getPos().x >= 1250)
            LOG.info("CHUNKLOAD {} {} new={}", event.getChunk().getPos().x, event.getChunk().getPos().z, event.isNewChunk());
    }

    @SubscribeEvent
    static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long dt = System.nanoTime() - tickStart;
        tickCount++;
        tickNanos += dt;
        maxTickNanos = Math.max(maxTickNanos, dt);
        DevRecipes.tick();
        if (!trace) return;
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) LOG.info("STRACE {}", line(p));
    }

    static boolean tracing() {
        return trace;
    }

    private static String line(ServerPlayer p) {
        var flight = p.getAttribute(ForgeMod.CREATIVE_FLIGHT);
        Entity vehicle = p.getVehicle();
        return String.format("gt=%d %s pos=%.3f,%.3f,%.3f v=%.3f,%.3f,%.3f state=%s grips=%d cf=%.0f flying=%b fall=%.2f hp=%.1f ground=%b air=%d mv=%.3f,%.3f,%.3f%s sneak=%b pose=%s veh=%s ride=%s vrot=%s",
                p.level().getGameTime(), p.getName().getString(), p.getX(), p.getY(), p.getZ(),
                p.getDeltaMovement().x, p.getDeltaMovement().y, p.getDeltaMovement().z,
                RigServer.state(p), RigServer.grips(p).size(), flight == null ? -1 : flight.getValue(),
                p.getAbilities().flying, p.fallDistance, p.getHealth(), p.onGround(), BacktankUtil.getAir(p.getItemBySlot(EquipmentSlot.CHEST)),
                RigServer.motion(p).x, RigServer.motion(p).y, RigServer.motion(p).z, shipFields(p), p.isShiftKeyDown(), p.getPose(),
                vehicle == null ? "-" : BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()),
                ServerChainConveyorHandler.hangingPlayers.containsKey(p.getUUID()) ? "chain" : "-",
                vehicle == null ? "-" : String.format(Locale.ROOT, "%.1f", vehicle.getYRot()));
    }

    /** {@code sgrips}: valid Grips on a Ship; {@code carry}: the Ship holding most of them (at least {@link Grips#HELD}), as RigServer picks it. */
    private static String shipFields(ServerPlayer p) {
        ShipFrames ships = ShipFrames.get();
        List<Frame> frames = new ArrayList<>();
        Frame best = null;
        int bestCount = Grips.HELD - 1;
        for (Grip g : RigServer.grips(p)) {
            Frame f = ships.isPlot(p.level(), g.block()) ? ships.containing(p.level(), g.block()) : null;
            if (f == null) continue;
            frames.add(f);
            int n = 0;
            for (Frame o : frames) if (f.equals(o)) n++;
            if (n > bestCount) {
                bestCount = n;
                best = f;
            }
        }
        return " sgrips=" + frames.size() + " carry=" + (best == null ? "-" : best.id());
    }

    private static int reply(CommandSourceStack source, String msg) {
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
