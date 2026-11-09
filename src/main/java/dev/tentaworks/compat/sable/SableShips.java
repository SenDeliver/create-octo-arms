package dev.tentaworks.compat.sable;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.tentaworks.compat.Frame;
import dev.tentaworks.compat.Pose;
import dev.tentaworks.compat.ShipBody;
import dev.tentaworks.compat.ShipFrames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sable 2.0 Ships through sable-companion (plus Sable internals: {@link #dropInherited}, and for Ship Grab {@link #body}:
 * ServerSubLevel, its mass tracker and RigidBodyHandle): the only class that touches Sable. Loaded only when Sable is.
 * Keeps, on the server thread, the poses of the last {@value #HISTORY} server ticks of every Ship near a player.
 */
public final class SableShips implements ShipFrames {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int HISTORY = 10;
    private static final int FORGET_TICKS = 40;
    private static final double TRACK_RADIUS = 24;
    private static final double UNIT = 1.0E-6;

    private final Map<UUID, History> history = new HashMap<>();
    private boolean noBodies;

    private static final class History {
        final ArrayDeque<Pose> poses = new ArrayDeque<>(HISTORY);
        int seen;
    }

    private SableShips() {
    }

    public static ShipFrames create() {
        SableShips ships = new SableShips();
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> ships.record(e));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ships.history.clear());
        return ships;
    }

    @Override
    public List<Frame> near(Level level, AABB box) {
        if (!ShipFrames.enabled()) return List.of();
        List<Frame> out = new ArrayList<>();
        for (SubLevelAccess s : SableCompanion.INSTANCE.getAllIntersecting(level, new BoundingBox3d(box))) out.add(frame(level, s));
        return out;
    }

    @Override
    public Frame containing(Level level, BlockPos pos) {
        if (!ShipFrames.enabled()) return null;
        SubLevelAccess s = SableCompanion.INSTANCE.getContaining(level, (Vec3i) pos);
        return s == null ? null : frame(level, s);
    }

    @Override
    public boolean isPlot(Level level, BlockPos pos) {
        return SableCompanion.INSTANCE.isInPlotGrid(level, (Vec3i) pos);
    }

    @Override
    public List<Pose> recent(Frame frame) {
        List<Pose> out = new ArrayList<>(HISTORY + 2);
        out.add(frame.pose());
        out.add(frame.last());
        if (frame.client()) return out;
        History h = history.get((UUID) frame.id());
        if (h != null) out.addAll(h.poses);
        return out;
    }

    @Override
    public boolean tracking(Entity entity, Frame frame) {
        if (frame == null) return false;
        SubLevelAccess s = SableCompanion.INSTANCE.getTrackingSubLevel(entity);
        return s != null && frame.id().equals(s.getUniqueId());
    }

    /** Sable internal (its LivingEntity mixin), the only one used: descriptor identical across Sable 1.0.4 to 2.0.6, declared range [2.0.0,2.1). */
    @Override
    public void dropInherited(Entity entity) {
        if (entity instanceof LivingEntityMovementExtension m) m.sable$getInheritedVelocity().zero();
    }

    /** Sable internals, same signatures in 2.0.0 to 2.0.6; a Sable without them refuses Ship Grab silently (logged once). */
    @Override
    public ShipBody body(Level level, BlockPos pos) {
        if (noBodies || level.isClientSide() || !ShipFrames.enabled()) return null;
        try {
            return SableCompanion.INSTANCE.getContaining(level, (Vec3i) pos) instanceof ServerSubLevel s && !s.isRemoved() ? new Body(level, s) : null;
        } catch (LinkageError e) {
            noBodies = true;
            LOG.warn("This Sable has no Ship bodies Octo Arms can use: Ship Grab is off", e);
            return null;
        }
    }

    /** Every call guarded: a Sable whose internals moved gives a dead body (released, never thrown) instead of a crash. */
    private record Body(Level level, ServerSubLevel ship) implements ShipBody {
        @Override
        public Object id() {
            return ship.getUniqueId();
        }

        @Override
        public Frame frame() {
            return SableShips.frame(level, ship);
        }

        @Override
        public double mass() {
            try {
                return ship.getMassTracker() == null ? -1 : ship.getMassTracker().getMass();
            } catch (LinkageError e) {
                return -1;
            }
        }

        @Override
        public boolean alive() {
            try {
                if (ship.isRemoved()) return false;
                RigidBodyHandle h = RigidBodyHandle.of(ship);
                return h != null && h.isValid();
            } catch (LinkageError e) {
                return false;
            }
        }

        @Override
        public void velocity(Vec3 linear, Vec3 angular) {
            try {
                RigidBodyHandle h = RigidBodyHandle.of(ship);
                if (h == null || !h.isValid()) return;
                Vector3d dv = new Vector3d(linear.x, linear.y, linear.z).sub(h.getLinearVelocity(new Vector3d()));
                Vector3d dw = new Vector3d(angular.x, angular.y, angular.z).sub(h.getAngularVelocity(new Vector3d()));
                h.addLinearAndAngularVelocity(dv, dw);
            } catch (LinkageError ignored) {
            }
        }
    }

    private void record(ServerTickEvent.Post e) {
        int now = e.getServer().getTickCount();
        Set<UUID> done = new HashSet<>();
        for (ServerLevel level : e.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                AABB box = player.getBoundingBox().inflate(TRACK_RADIUS);
                for (SubLevelAccess s : SableCompanion.INSTANCE.getAllIntersecting(level, new BoundingBox3d(box))) {
                    UUID id = s.getUniqueId();
                    if (!done.add(id)) continue;
                    History h = history.computeIfAbsent(id, k -> new History());
                    if (h.poses.size() == HISTORY) h.poses.removeLast();
                    h.poses.addFirst(pose(s.logicalPose()));
                    h.seen = now;
                }
            }
        }
        history.values().removeIf(h -> now - h.seen > FORGET_TICKS);
    }

    private static Frame frame(Level level, SubLevelAccess s) {
        Pose3dc logical = s.logicalPose();
        Pose3dc lastPose = s.lastPose();
        Pose pose = pose(logical);
        Pose last = lastPose == null ? pose : pose(lastPose);
        Frame.RenderPoses render = level.isClientSide() ? pt -> last.lerp(pose, pt) : null;
        return new Frame(s.getUniqueId(), pose, last, unit(logical.scale()), level.isClientSide(), render);
    }

    private static Pose pose(Pose3dc p) {
        return new Pose(p.position(), p.orientation(), p.rotationPoint());
    }

    private static boolean unit(Vector3dc scale) {
        return Math.abs(scale.x() - 1) < UNIT && Math.abs(scale.y() - 1) < UNIT && Math.abs(scale.z() - 1) < UNIT;
    }
}
