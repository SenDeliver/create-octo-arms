package dev.tentaworks.dev;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.compat.ShipFrames;
import dev.tentaworks.config.TentaworksConfig;
import dev.tentaworks.registry.TentaworksAttachments;
import dev.tentaworks.registry.TentaworksDataComponents;
import dev.tentaworks.rig.Actuators;
import dev.tentaworks.rig.FarUse;
import dev.tentaworks.rig.Grab;
import dev.tentaworks.rig.Grips;
import dev.tentaworks.rig.Pinned;
import dev.tentaworks.rig.RigAir;
import dev.tentaworks.rig.RigEnchants;
import dev.tentaworks.rig.RigEvents;
import dev.tentaworks.rig.RigSettings;
import dev.tentaworks.rig.WorkSight;
import dev.tentaworks.rig.WornRig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dev source set only: Actuator tooling for the faruse, grab, shipgrab and look suites. Commands: fit|unfit (the source
 * player's worn Rig, or every player's, gains or loses its Actuators, SFIT), deft on|off (Deft on the worn Rig, SDEFT),
 * farmode always|rig_out|off (Rig Settings Far Use of the worn Rig, SFARMODE), config PATH VALUE (a server config value until
 * restart, SCONFIG), tame TARGETS OWNER (TAME). Lines: SGRAB (every Grab start, refusal, release, throw), SRIGEVENT (every
 * Rig event), MHURT (damage to any living entity but a player), SFAR (every block click, break, entity use and attack after
 * Far Use decided: lift, where it lies (PLOT: a Ship block, left to Sable), sight, cancelled), SDROPS (where the drops of a player's block break end up), and with
 * trace on SMOB (every entity tagged {@code tw}) plus the STRACE fields of {@link #fields}.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID)
public final class DevActuators {
    private static final Logger LOG = LoggerFactory.getLogger("tentaworks-dev");
    private static Field debtField;

    private DevActuators() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("fit").executes(c -> fit(c.getSource(), true)))
                .then(Commands.literal("unfit").executes(c -> fit(c.getSource(), false)))
                .then(Commands.literal("deft").then(Commands.argument("on", StringArgumentType.word()).executes(c -> {
                    boolean on = StringArgumentType.getString(c, "on").equals("on");
                    Holder<Enchantment> deft = c.getSource().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(RigEnchants.DEFT);
                    for (ServerPlayer p : targets(c.getSource())) {
                        ItemStack rig = WornRig.of(p).stack();
                        if (rig.isEmpty()) continue;
                        EnchantmentHelper.updateEnchantments(rig, m -> m.set(deft, on ? 1 : 0));
                        LOG.info("SDEFT gt={} {} deft={}", p.level().getGameTime(), p.getName().getString(), RigEnchants.deft(rig));
                    }
                    return reply(c.getSource(), "deft " + on);
                })))
                .then(Commands.literal("farmode").then(Commands.argument("mode", StringArgumentType.word()).executes(c -> {
                    RigSettings.FarUseMode mode = RigSettings.FarUseMode.valueOf(StringArgumentType.getString(c, "mode").toUpperCase(Locale.ROOT));
                    for (ServerPlayer p : targets(c.getSource())) {
                        ItemStack rig = WornRig.of(p).stack();
                        if (rig.isEmpty()) continue;
                        RigSettings s = rig.getOrDefault(TentaworksDataComponents.RIG_SETTINGS.get(), RigSettings.DEFAULT);
                        rig.set(TentaworksDataComponents.RIG_SETTINGS.get(), new RigSettings(s.standingHeight(), s.speed(), s.activation(), mode));
                        LOG.info("SFARMODE gt={} {} mode={}", p.level().getGameTime(), p.getName().getString(), RigSettings.of(rig).farUse());
                    }
                    return reply(c.getSource(), "farmode " + mode);
                })))
                .then(Commands.literal("config").then(Commands.argument("path", StringArgumentType.string()).then(Commands.argument("value", StringArgumentType.word()).executes(c -> {
                    String path = StringArgumentType.getString(c, "path"), raw = StringArgumentType.getString(c, "value");
                    Object v = TentaworksConfig.SPEC.getValues().get(path);
                    if (!(v instanceof ModConfigSpec.ConfigValue<?> cv)) return reply(c.getSource(), "no config " + path);
                    setConfig(cv, raw);
                    LOG.info("SCONFIG gt={} path={} value={}", c.getSource().getLevel().getGameTime(), path, cv.get());
                    return reply(c.getSource(), "config " + path + " " + cv.get());
                }))))
                .then(Commands.literal("tame").then(Commands.argument("targets", EntityArgument.entities()).then(Commands.argument("owner", EntityArgument.player()).executes(c -> {
                    ServerPlayer owner = EntityArgument.getPlayer(c, "owner");
                    int n = 0;
                    for (Entity e : EntityArgument.getEntities(c, "targets")) {
                        if (!(e instanceof TamableAnimal t)) continue;
                        t.tame(owner);
                        n++;
                        LOG.info("TAME gt={} id={} type={} owner={}", e.level().getGameTime(), e.getId(), type(e), owner.getName().getString());
                    }
                    return reply(c.getSource(), "tamed " + n);
                }))));
    }

    private static int fit(CommandSourceStack source, boolean on) {
        for (ServerPlayer p : targets(source)) {
            ItemStack rig = WornRig.of(p).stack();
            if (rig.isEmpty()) continue;
            if (on) rig.set(TentaworksDataComponents.ACTUATORS.get(), Unit.INSTANCE);
            else rig.remove(TentaworksDataComponents.ACTUATORS.get());
            LOG.info("SFIT gt={} {} fitted={} rig={}", p.level().getGameTime(), p.getName().getString(), Actuators.fitted(p), rig.getItemHolder().getRegisteredName());
        }
        return reply(source, on ? "fit" : "unfit");
    }

    private static List<ServerPlayer> targets(CommandSourceStack source) {
        ServerPlayer self = source.getPlayer();
        return self != null ? List.of(self) : source.getServer().getPlayerList().getPlayers();
    }

    @SuppressWarnings("unchecked")
    private static void setConfig(ModConfigSpec.ConfigValue<?> cv, String raw) {
        Object cur = cv.get();
        Object v = cur instanceof Boolean ? Boolean.parseBoolean(raw) : cur instanceof Integer ? Integer.parseInt(raw)
                : cur instanceof Double ? Double.parseDouble(raw) : raw;
        ((ModConfigSpec.ConfigValue<Object>) cv).set(v);
    }

    /** Observers of the Far Use decision: added once the server runs, so they come after Far Use's own LOWEST listeners and see their cancels. */
    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        Grab.listen(DevActuators::grabEvent);
        RigEvents.listen(e -> LOG.info("SRIGEVENT gt={} {} event={} number={}", e.player().level().getGameTime(), e.player().getName().getString(), e.event(),
                String.format(Locale.ROOT, "%.3f", e.number())));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayerInteractEvent.LeftClickBlock.class, e -> {
            if (e.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START && e.getEntity() instanceof ServerPlayer p)
                farBlock(p, "lclick", e.getPos(), null, e.isCanceled());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayerInteractEvent.RightClickBlock.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) farBlock(p, "rclick", e.getPos(), e.getHitVec().getLocation(), e.isCanceled());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BlockEvent.BreakEvent.class, e -> {
            if (e.getPlayer() instanceof ServerPlayer p) farBlock(p, "break", e.getPos(), null, e.isCanceled());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayerInteractEvent.EntityInteract.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) farEntity(p, "interact", e.getTarget(), e.isCanceled());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, AttackEntityEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) farEntity(p, "attack", e.getTarget(), e.isCanceled());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BlockDropsEvent.class, e -> {
            if (!(e.getBreaker() instanceof ServerPlayer p)) return;
            List<String> items = new ArrayList<>();
            double near = 0;
            for (ItemEntity it : e.getDrops()) {
                items.add(BuiltInRegistries.ITEM.getKey(it.getItem().getItem()) + "x" + it.getItem().getCount());
                near = Math.max(near, it.position().distanceTo(p.position()));
            }
            LOG.info("SDROPS gt={} {} pos={} n={} items={} far={} xp={}", p.level().getGameTime(), p.getName().getString(), e.getPos().toShortString().replace(" ", ""),
                    e.getDrops().size(), items.isEmpty() ? "-" : String.join(",", items), String.format(Locale.ROOT, "%.3f", near), e.getDroppedExperience());
        });
    }

    private static void farBlock(ServerPlayer p, String kind, BlockPos pos, @Nullable Vec3 point, boolean canceled) {
        Vec3 eye = p.getEyePosition();
        double d2 = new AABB(pos).distanceToSqr(eye);
        AttributeInstance range = p.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        double lift = lift(range, FarUse.REACH_ID);
        LOG.info("SFAR gt={} {} kind={} at={} type={} dist={} range={} lift={} far={} sight={} asking={} canceled={}", p.level().getGameTime(), p.getName().getString(), kind,
                pos.toShortString().replace(" ", ""), BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(pos).getBlock()), f3(Math.sqrt(d2)), f3(range == null ? 0 : range.getValue()),
                f3(lift), ShipFrames.get().isPlot(p.level(), pos) ? "PLOT" : lift > 0 ? FarUse.far(d2, range.getValue() - lift, range.getValue()) : FarUse.Far.NEAR,
                WorkSight.seesBlock(p.level(), pos, point, eye, Grips.body(p)), Grab.asking(), canceled);
    }

    private static void farEntity(ServerPlayer p, String kind, Entity target, boolean canceled) {
        Vec3 eye = p.getEyePosition();
        double d2 = target.getBoundingBox().distanceToSqr(eye);
        AttributeInstance range = p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        double lift = lift(range, FarUse.STRIKE_ID);
        LOG.info("SFAR gt={} {} kind={} at={} type={} dist={} range={} lift={} far={} sight={} asking={} canceled={}", p.level().getGameTime(), p.getName().getString(), kind,
                target.getId(), type(target), f3(Math.sqrt(d2)), f3(range == null ? 0 : range.getValue()), f3(lift),
                lift > 0 ? FarUse.far(d2, range.getValue() - lift, range.getValue()) : FarUse.Far.NEAR,
                WorkSight.seesBox(p.level(), p, target.getBoundingBox(), eye, Grips.body(p)), Grab.asking(), canceled);
    }

    static void grabEvent(Grab.Event e) {
        LOG.info("SGRAB gt={} {} kind={} target={} type={} why={} actuators={} speed={} mass={}", e.wearer().level().getGameTime(), e.wearer().getName().getString(), e.kind(),
                e.target() == null ? -1 : e.target().getId(), e.target() == null ? (e.mass() >= 0 ? "ship" : "-") : type(e.target()),
                e.why().isEmpty() ? "-" : e.why(), e.actuators(), f3(e.speed()), String.format(Locale.ROOT, "%.1f", e.mass()));
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity e = event.getEntity();
        if (e instanceof Player) return;
        Entity by = event.getSource().getEntity();
        LOG.info("MHURT gt={} id={} type={} source={} amount={} by={}", e.level().getGameTime(), e.getId(), type(e), event.getSource().getMsgId(),
                String.format(Locale.ROOT, "%.2f", event.getNewDamage()), by == null ? "-" : by instanceof Player pl ? pl.getName().getString() : type(by));
    }

    /** Every entity tagged {@code tw}, once per tick with trace on. */
    static void traceMobs(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (!e.getTags().contains("tw")) continue;
                Pinned pin = e.getExistingDataOrNull(TentaworksAttachments.PINNED);
                Entity veh = e.getVehicle();
                LOG.info("SMOB gt={} id={} type={} pos={} v={} nograv={} ground={} hp={} fall={} box={} veh={} pinned={} thrown={} swell={} target={}", level.getGameTime(), e.getId(),
                        type(e), vec(e.position()), vec(e.getDeltaMovement()), e.isNoGravity(), e.onGround(),
                        e instanceof LivingEntity l ? String.format(Locale.ROOT, "%.1f", l.getHealth()) : "-", String.format(Locale.ROOT, "%.2f", e.fallDistance),
                        vec(e.getBoundingBox().getCenter()), veh == null ? "-" : type(veh), pin == null ? "-" : pin.held() ? "held" : pin.calm(level.getGameTime()) ? "calm" : "spent",
                        e.getExistingDataOrNull(TentaworksAttachments.THROWN) != null, e instanceof Creeper c ? String.format(Locale.ROOT, "%.3f", c.getSwelling(1.0F)) : "-",
                        e instanceof Mob m && m.getTarget() != null ? m.getTarget().getId() : -1);
            }
        }
    }

    /**
     * STRACE fields (end of line): Far Use lift of the block and entity range, the Grab (held id, heavy, Ship block, hold point,
     * Ship mass), the Actuator Air debt not yet taken, the movement speed attribute, total XP, fitted, Deft, Far Use mode.
     */
    static String fields(ServerPlayer p) {
        ItemStack rig = WornRig.of(p).stack();
        Entity held = Grab.target(p);
        BlockPos ship = Grab.ship(p);
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        return String.format(Locale.ROOT, " lift=%.3f,%.3f gheld=%d gheavy=%b gship=%s gcentre=%s gmass=%.1f adebt=%.4f mspd=%.4f xp=%d fitted=%b deft=%b farmode=%s",
                lift(p.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), FarUse.REACH_ID), lift(p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), FarUse.STRIKE_ID),
                held == null ? -1 : held.getId(), Grab.heavy(p), ship == null ? "-" : ship.toShortString().replace(" ", ""), vec(Grab.centre(p)), Grab.shipMass(p),
                debt(p), speed == null ? 0 : speed.getValue(), p.totalExperience, Actuators.fitted(rig), RigEnchants.deft(rig),
                rig.isEmpty() ? "-" : RigSettings.of(rig).farUse().getSerializedName());
    }

    private static double lift(@Nullable AttributeInstance inst, ResourceLocation id) {
        AttributeModifier m = inst == null ? null : inst.getModifier(id);
        return m == null ? 0 : m.amount();
    }

    @SuppressWarnings("unchecked")
    private static double debt(Player p) {
        try {
            if (debtField == null) {
                debtField = RigAir.class.getDeclaredField("ACTUATOR_DEBT");
                debtField.setAccessible(true);
            }
            return ((Map<Player, Double>) debtField.get(null)).getOrDefault(p, 0.0);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    static String type(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }

    private static String f3(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static String vec(@Nullable Vec3 v) {
        return v == null ? "-" : String.format(Locale.ROOT, "%.3f,%.3f,%.3f", v.x, v.y, v.z);
    }

    private static int reply(CommandSourceStack source, String msg) {
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
