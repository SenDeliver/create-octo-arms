package dev.tentaworks.rig;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.mixin.ServerGamePacketListenerImplAccessor;
import dev.tentaworks.network.RigMotionPacket;
import dev.tentaworks.network.RigStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the Rig: trusts the client's motion (adr/0001) but checks the Rig is worn and the Grips are real,
 * mirrors the velocity and keeps the float kick quiet with a transient creative-flight modifier while the Rig carries.
 * Air is the server's call: it drains here, an empty Rig cannot activate and a carrying one drops to Crawl.
 * So is wear: a worn-out Rig (1 durability) cannot activate and an Active one Sets Down.
 */
public final class RigServer {
    private static final Logger LOG = LogUtils.getLogger();
    public static final ResourceLocation FLOAT_ID = TentaworksIds.id("rig_float");
    private static final AttributeModifier FLOAT = new AttributeModifier(FLOAT_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);
    private static final double GRIP_SLACK = 0.75;
    private static final int REJECT_TICKS = 10;
    private static final int SILENT_TICKS = 10;
    private static final double CRAWL_RISE = 0.05;
    private static final double CRAWL_SLACK = 1.5;
    private static final double FALL_STEP = 0.1;
    private static final int RESEND_TICKS = 10;
    private static final int SPEED_WINDOW = 10;
    private static final double REAL_STILL = 0.01;
    private static final double SPEED_SLACK = 1.25;
    private static final double SPEED_EXTRA = 1.0;
    private static final int LOG_QUIET_TICKS = 200;
    private static final Map<UUID, Wearer> WEARERS = new HashMap<>();
    private static final Map<UUID, Double> LAST_Y = new HashMap<>();
    private static final Map<UUID, RigMotionPacket> PENDING = new HashMap<>();
    private static final Map<UUID, Integer> BAD_PACKET_LOGGED = new HashMap<>();
    private static long processed;
    private static long relays;
    private static boolean floatFix = true;
    private static boolean fallFix = true;

    private static final class Wearer {
        RigState state = RigState.STOWED;
        List<Grip> grips = List.of();
        Tentacles tentacles = Tentacles.NONE;
        int unheldTicks;
        boolean rejected;
        int lastPacket;
        Vec3 motion = Vec3.ZERO;
        double airDebt;
        RigState told;
        Vec3 lastPos;
        int crawlBad;
        int downBad;
        boolean airRejected;
        int resentAt = -100;
        Vec3 tickPos;
        final double[] window = new double[SPEED_WINDOW];
        int windowAt;
        int overSpeed;
        Vec3 realMove = Vec3.ZERO;
        RigState relayedState;
        Tentacles relayed;
    }

    private RigServer() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                fallGuard(p);
                tick(p);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> processPending(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            for (Map.Entry<UUID, Wearer> en : WEARERS.entrySet()) {
                ServerPlayer p = e.getServer().getPlayerList().getPlayer(en.getKey());
                if (p != null) trackRealMove(p, en.getValue());
            }
        });
        NeoForge.EVENT_BUS.addListener((EntityTeleportEvent e) -> {
            Wearer w = WEARERS.get(e.getEntity().getUUID());
            if (w != null) {
                w.tickPos = null;
                Arrays.fill(w.window, 0);
                w.overSpeed = 0;
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e) -> {
            if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer watcher) {
                Wearer w = WEARERS.get(target.getUUID());
                if (w != null) PacketDistributor.sendToPlayer(watcher, new RigStatePacket(target.getId(), w.state, w.tentacles));
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                setFloat(p, false);
                WEARERS.remove(p.getUUID());
                LAST_Y.remove(p.getUUID());
                PENDING.remove(p.getUUID());
                BAD_PACKET_LOGGED.remove(p.getUUID());
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) stow(p, true);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) stow(p, true);
        });
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) stow(p, true);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            WEARERS.clear();
            LAST_Y.clear();
            PENDING.clear();
            BAD_PACKET_LOGGED.clear();
        });
    }

    public static RigState state(ServerPlayer player) {
        Wearer w = WEARERS.get(player.getUUID());
        return w == null ? RigState.STOWED : w.state;
    }

    public static List<Grip> grips(ServerPlayer player) {
        Wearer w = WEARERS.get(player.getUUID());
        return w == null ? List.of() : w.grips;
    }

    public static Vec3 motion(ServerPlayer player) {
        Wearer w = WEARERS.get(player.getUUID());
        return w == null ? Vec3.ZERO : w.motion;
    }

    /** Motion packets handled and state relays sent since start (server stats). */
    public static long[] stats() {
        return new long[]{processed, relays};
    }

    public static void setFloatFix(boolean on) {
        floatFix = on;
    }

    public static void setFallFix(boolean on) {
        fallFix = on;
    }

    /**
     * Vanilla's float check zeroes the vertical move before its "not falling" test, so any 80 ticks in free air kick,
     * even a plain fall. A Rig wearer may fall far before a mid-air Double Jump catches them: while the server sees
     * them really dropping, the counter restarts. A hovering client gains nothing. Only for a Rig that could still catch:
     * Stowed or Falling, with Air and not worn out.
     */
    private static void fallGuard(ServerPlayer player) {
        double y = player.getY();
        Double last = LAST_Y.put(player.getUUID(), y);
        if (!fallFix || last == null || player.connection == null || !TentacleRigItem.isWorn(player)) return;
        RigState state = state(player);
        if (state.carries() || !RigAir.has(player) || TentacleRigItem.wornOut(RigAir.worn(player))) return;
        if (last - y > FALL_STEP) ((ServerGamePacketListenerImplAccessor) player.connection).tentaworks$setAboveGroundTickCount(0);
    }

    public static double reach(ServerPlayer player) {
        return TentacleRigItem.reach(player);
    }

    /**
     * Network side: keeps only the latest packet per player; {@link #processPending} handles it once per server tick.
     * A Stowed packet is handled at once, so a later packet in the same tick cannot hide the stow.
     * A non-finite packet stows at once and is logged at most once per {@value #LOG_QUIET_TICKS} ticks per player.
     */
    public static void onMotion(ServerPlayer player, RigMotionPacket packet) {
        if (!finite(packet.motion()) || packet.tentacles().list().stream().anyMatch(g -> !finite(g.point()))) {
            PENDING.remove(player.getUUID());
            int now = player.server.getTickCount();
            Integer last = BAD_PACKET_LOGGED.get(player.getUUID());
            if (last == null || now - last > LOG_QUIET_TICKS) {
                BAD_PACKET_LOGGED.put(player.getUUID(), now);
                LOG.warn("Tentacle Rig of {} sent a non-finite motion or Grip: stowing (repeats not logged for 10 s)", player.getName().getString());
            }
            stow(player, true);
            return;
        }
        if (packet.state() == RigState.STOWED) {
            PENDING.remove(player.getUUID());
            stow(player, false);
            return;
        }
        PENDING.put(player.getUUID(), packet);
    }

    private static void processPending(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        List<Map.Entry<UUID, RigMotionPacket>> batch = new ArrayList<>(PENDING.entrySet());
        PENDING.clear();
        for (Map.Entry<UUID, RigMotionPacket> e : batch) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player != null) process(player, e.getValue());
        }
    }

    private static void process(ServerPlayer player, RigMotionPacket packet) {
        processed++;
        if (packet.state() == RigState.STOWED || !TentacleRigItem.isWorn(player) || !player.isAlive()) {
            stow(player, packet.state() != RigState.STOWED);
            return;
        }
        Wearer w = WEARERS.get(player.getUUID());
        boolean air = RigAir.has(player);
        boolean worn = TentacleRigItem.wornOut(RigAir.worn(player));
        if (w == null && (!air || worn)) {
            PacketDistributor.sendToPlayer(player, new RigStatePacket(player.getId(), RigState.STOWED, Tentacles.NONE));
            return;
        }
        if (w == null) {
            w = new Wearer();
            WEARERS.put(player.getUUID(), w);
        }
        Vec3 body = Grips.body(player);
        double reach = reach(player) + GRIP_SLACK;
        Grip[] kept = new Grip[Tentacles.SLOTS];
        boolean ghost = false;
        for (int i = 0; i < Tentacles.SLOTS; i++) {
            Grip g = packet.tentacles().grips()[i];
            if (g == null) continue;
            if (Grips.valid(player.level(), g, body, reach)) kept[i] = g;
            else ghost = true;
        }
        Tentacles tentacles = packet.tentacles().only(kept);
        List<Grip> grips = tentacles.list();
        boolean held = grips.size() >= Grips.HELD;
        RigState state = wornState(w, airState(w, packet.state(), air, RigAir.free(player)), worn);
        if (w.state == RigState.FALLING && state == RigState.CRAWL) w.crawlBad = 0;
        if (state == RigState.CRAWL && crawlViolation(player, w, packet.motion()) && ++w.crawlBad > REJECT_TICKS) {
            LOG.warn("Tentacle Rig of {} moves against Crawl with no Air: forcing Falling", player.getName().getString());
            state = RigState.FALLING;
            w.airRejected = true;
        }
        if (state == RigState.SETTING_DOWN) {
            if (!settingDownViolation(player, w, packet.motion())) {
                w.downBad = 0;
            } else if (++w.downBad > REJECT_TICKS) {
                LOG.warn("Tentacle Rig of {} moves against SETTING_DOWN: stowing", player.getName().getString());
                stow(player, true);
                return;
            }
        }
        w.lastPos = player.position();
        if (state != packet.state()) {
            if (w.told != state || (state == RigState.FALLING && player.tickCount - w.resentAt >= RESEND_TICKS)) {
                w.told = state;
                w.resentAt = player.tickCount;
                PacketDistributor.sendToPlayer(player, new RigStatePacket(player.getId(), state, tentacles));
            }
        } else {
            w.told = null;
        }
        if (state.carries()) {
            if (held) {
                w.rejected = false;
                w.unheldTicks = 0;
            } else if (w.rejected) {
                state = RigState.FALLING;
                resendFalling(player, w, tentacles, packet, ghost);
            } else if (++w.unheldTicks > REJECT_TICKS) {
                LOG.warn("Tentacle Rig of {} claims {} with {} valid Grips: forcing Falling", player.getName().getString(), state, grips.size());
                state = RigState.FALLING;
                w.rejected = true;
                w.told = state;
                w.resentAt = player.tickCount;
                PacketDistributor.sendToPlayer(player, new RigStatePacket(player.getId(), state, tentacles));
            }
        }
        w.state = state;
        w.grips = grips;
        w.tentacles = tentacles;
        w.lastPacket = player.tickCount;
        if (state.carries() && held) {
            Vec3 motion = packet.motion();
            if (state == RigState.CRAWL) {
                motion = new Vec3(0, Mth.clamp(motion.y, -RigAir.crawlStep() * 1.05, 0), 0);
            } else if (state == RigState.SETTING_DOWN) {
                motion = new Vec3(0, Mth.clamp(motion.y, -maxStep(player), 0), 0);
            } else {
                double max = maxStep(player);
                if (motion.length() > max) motion = motion.normalize().scale(max);
            }
            w.motion = motion;
            player.setDeltaMovement(motion);
            player.resetFallDistance();
            setFloat(player, true);
            noFlying(player);
            noGliding(player);
        } else {
            w.motion = Vec3.ZERO;
            setFloat(player, false);
        }
        relay(player, w);
    }

    /** Trackers get the Rig only when its state or Tentacles change (and once when they start tracking). */
    private static void relay(ServerPlayer player, Wearer w) {
        if (w.state == w.relayedState && sameTentacles(w.tentacles, w.relayed)) return;
        w.relayedState = w.state;
        w.relayed = w.tentacles;
        relays++;
        PacketDistributor.sendToPlayersTrackingEntity(player, new RigStatePacket(player.getId(), w.state, w.tentacles));
    }

    private static boolean sameTentacles(Tentacles a, Tentacles b) {
        return b != null && Arrays.equals(a.grips(), b.grips()) && Arrays.equals(a.phases(), b.phases());
    }

    /**
     * A rejected client that keeps claiming to carry hears Falling again every {@value #RESEND_TICKS} ticks. If it claimed
     * Grips the server does not see, those blocks are re-sent too: an honest client may hold on to a ghost block.
     */
    private static void resendFalling(ServerPlayer player, Wearer w, Tentacles tentacles, RigMotionPacket packet, boolean ghost) {
        if (!packet.state().carries() || player.tickCount - w.resentAt < RESEND_TICKS) return;
        w.resentAt = player.tickCount;
        w.told = RigState.FALLING;
        PacketDistributor.sendToPlayer(player, new RigStatePacket(player.getId(), RigState.FALLING, tentacles));
        if (!ghost) return;
        Vec3 body = Grips.body(player);
        double near = reach(player) + GRIP_SLACK + 2;
        for (Grip g : packet.tentacles().list()) {
            BlockPos b = g.block();
            if (Vec3.atCenterOf(b).distanceToSqr(body) > near * near) continue;
            for (BlockPos pos : new BlockPos[]{b, b.relative(g.face())})
                if (player.level().isLoaded(pos)) player.connection.send(new ClientboundBlockUpdatePacket(player.level(), pos));
        }
    }

    private static double maxStep(ServerPlayer player) {
        return TentacleRigItem.topSpeed(RigAir.worn(player)) / 20.0 * 1.05;
    }

    /**
     * Activating needs Air, a carrying Rig with none left Crawls, and a Falling one with none left grabs again into Crawl.
     * Crawl never turns back into Active: Air comes back only in a Rig Block. A Crawl dropped by silence or by losing
     * Grips resumes on the next Crawl claim; one dropped for moving against Crawl stays Falling until Stowed.
     * Creative players need no Air: never Crawl.
     */
    private static RigState airState(Wearer w, RigState claim, boolean air, boolean free) {
        boolean carryClaim = claim.carries();
        if (free) return claim == RigState.CRAWL ? RigState.ACTIVE : claim;
        if (air) return w.state == RigState.CRAWL && carryClaim ? RigState.CRAWL : claim;
        if (!carryClaim) return claim;
        if (w.state != RigState.FALLING) return RigState.CRAWL;
        return claim == RigState.CRAWL && !w.airRejected ? RigState.CRAWL : RigState.FALLING;
    }

    /** A worn-out Rig cannot be Activated: an Active one Sets Down, a Falling one does not catch again. */
    private static RigState wornState(Wearer w, RigState state, boolean worn) {
        if (!worn) return state;
        if (w.state == RigState.FALLING && state.carries()) return RigState.FALLING;
        return state == RigState.ACTIVE ? RigState.SETTING_DOWN : state;
    }

    private static boolean crawlViolation(ServerPlayer player, Wearer w, Vec3 claimed) {
        double max = RigAir.crawlStep() * CRAWL_SLACK + 0.05;
        if (claimed.y > 1.0E-3 || claimed.horizontalDistance() > max) return true;
        if (w.lastPos == null) return false;
        Vec3 d = player.position().subtract(w.lastPos);
        return d.y > CRAWL_RISE || d.horizontalDistance() > max;
    }

    /** Setting Down only lowers the wearer: a claimed or real rise, or a real sideways move, is a violation. */
    private static boolean settingDownViolation(ServerPlayer player, Wearer w, Vec3 claimed) {
        if (claimed.y > 1.0E-3 || claimed.horizontalDistance() > 1.0E-3) return true;
        if (w.lastPos == null) return false;
        Vec3 d = player.position().subtract(w.lastPos);
        return d.y > CRAWL_RISE || d.horizontalDistance() > maxStep(player) * CRAWL_SLACK + 0.05;
    }

    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static void tick(ServerPlayer player) {
        Wearer w = WEARERS.get(player.getUUID());
        if (w == null) return;
        if (!TentacleRigItem.isWorn(player)) {
            stow(player, true);
            return;
        }
        if (!w.state.carries()) return;
        noFlying(player);
        noGliding(player);
        if (w.overSpeed > REJECT_TICKS) {
            LOG.warn("Tentacle Rig of {} moves faster than its top Speed: stowing", player.getName().getString());
            stow(player, true);
            return;
        }
        Vec3 body = Grips.body(player);
        double reach = reach(player) + GRIP_SLACK;
        if (player.tickCount - w.lastPacket > SILENT_TICKS) {
            w.state = RigState.FALLING;
            w.motion = Vec3.ZERO;
            setFloat(player, false);
            relay(player, w);
        } else if (w.grips.stream().filter(g -> Grips.valid(player.level(), g, body, reach)).count() < Grips.HELD) {
            setFloat(player, false);
        } else if (w.state == RigState.CRAWL && player.onGround()) {
            stow(player, true);
        } else if (w.state.drains() && !RigAir.free(player)) {
            drain(player, w);
        }
    }

    /**
     * The server's own view of the wearer's move: position delta per server tick, read after all players ticked (during the
     * player tick vanilla moves a ServerPlayer by its velocity and then puts it back), teleport events reset it. Feeds the Air rate
     * (never below what the real move costs) and the speed check (a {@value #SPEED_WINDOW}-tick window, so bursty move
     * packets after lag do not count as speeding).
     */
    private static void trackRealMove(ServerPlayer player, Wearer w) {
        Vec3 pos = player.position();
        Vec3 d = w.tickPos == null ? Vec3.ZERO : pos.subtract(w.tickPos);
        w.tickPos = pos;
        w.realMove = d.lengthSqr() < REAL_STILL * REAL_STILL ? Vec3.ZERO : d;
        w.window[w.windowAt] = d.length();
        w.windowAt = (w.windowAt + 1) % SPEED_WINDOW;
        if (!w.state.carries()) {
            w.overSpeed = 0;
            return;
        }
        double sum = 0;
        for (double v : w.window) sum += v;
        if (sum > maxStep(player) * SPEED_WINDOW * SPEED_SLACK + SPEED_EXTRA) w.overSpeed++;
        else w.overSpeed = 0;
    }

    private static void drain(ServerPlayer player, Wearer w) {
        ItemStack rig = RigAir.worn(player);
        w.airDebt += Math.max(RigAir.perTick(RigAir.use(w.motion), rig), RigAir.perTick(RigAir.use(w.realMove), rig));
        if (w.airDebt >= 1) {
            int n = (int) w.airDebt;
            w.airDebt -= n;
            BacktankUtil.consumeAir(player, rig, n);
        }
        if (BacktankUtil.getAir(rig) > 0) return;
        w.state = RigState.CRAWL;
        w.told = RigState.CRAWL;
        RigStatePacket packet = new RigStatePacket(player.getId(), RigState.CRAWL, w.tentacles);
        PacketDistributor.sendToPlayer(player, packet);
        PacketDistributor.sendToPlayersTrackingEntity(player, packet);
    }

    public static void stow(ServerPlayer player, boolean tellWearer) {
        Wearer w = WEARERS.remove(player.getUUID());
        setFloat(player, false);
        if (w == null) return;
        RigStatePacket packet = new RigStatePacket(player.getId(), RigState.STOWED, Tentacles.NONE);
        if (tellWearer) PacketDistributor.sendToPlayer(player, packet);
        PacketDistributor.sendToPlayersTrackingEntity(player, packet);
    }

    private static void noFlying(ServerPlayer player) {
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    private static void noGliding(ServerPlayer player) {
        if (player.isFallFlying()) player.stopFallFlying();
    }

    private static void setFloat(ServerPlayer player, boolean on) {
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight == null) return;
        if (on && floatFix) {
            if (!flight.hasModifier(FLOAT_ID)) flight.addTransientModifier(FLOAT);
            if (player.connection != null) ((ServerGamePacketListenerImplAccessor) player.connection).tentaworks$setAboveGroundTickCount(0);
        } else if (flight.hasModifier(FLOAT_ID)) {
            flight.removeModifier(FLOAT_ID);
        }
    }
}
