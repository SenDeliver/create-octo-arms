package dev.tentaworks.compat;

import dev.tentaworks.compat.sable.SableShips;
import dev.tentaworks.config.TentaworksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The only door to Sable Ships. {@link NoShips} without Sable; the Sable implementation is loaded only when Sable is.
 * With the server config {@code ships.grip} off, no Ship is near and no plot block has a Ship.
 */
public interface ShipFrames {
    static ShipFrames get() {
        return Holder.INSTANCE;
    }

    /** Picks the implementation now (mod construction), so the server pose history runs from the first tick. */
    static void init() {
        get();
    }

    /** Unit- and other-scale Ships whose bounds meet {@code box} (world coords). */
    List<Frame> near(Level level, AABB box);

    /** The Ship owning a plot block, or null (world block, Ship gone, or Ships off). */
    Frame containing(Level level, BlockPos pos);

    /** The Ship owning a plot position whatever the config (where it shows in the world), or null (world position, Ship gone). */
    Frame owner(Level level, BlockPos pos);

    /** Whether a position is in Sable's plot grid (Ship space), whatever the config. */
    boolean isPlot(Level level, BlockPos pos);

    /**
     * Poses a Grip on this Ship may be checked against: the current and last pose, plus on the server the poses of the
     * last few server ticks (client Grips are built from a lagging pose).
     */
    List<Pose> recent(Frame frame);

    /** Whether Sable already moves {@code entity} with this Ship (it stands on the deck), so the Rig must not carry it too. */
    boolean tracking(Entity entity, Frame frame);

    /** Drops the Ship velocity Sable keeps on an entity that left a deck: the Rig, not that momentum, moves a Held wearer. */
    void dropInherited(Entity entity);

    /** The server body of the Ship owning a plot block, or null (world block, Ship gone, Ships off, a client, or a Sable without the internals Ship Grab needs). */
    ShipBody body(Level level, BlockPos pos);

    /** World point of a point given in the frame of {@code block}: unchanged for world blocks or when the Ship is gone. */
    default Vec3 toWorld(Level level, BlockPos block, Vec3 point) {
        if (!isPlot(level, block)) return point;
        Frame frame = containing(level, block);
        return frame == null ? point : frame.toWorld(point);
    }

    /** World point of a point given in the frame of {@code block}: unchanged for world blocks, null for a plot block whose Ship is gone. */
    default @Nullable Vec3 worldOf(Level level, BlockPos block, Vec3 point) {
        if (!isPlot(level, block)) return point;
        Frame frame = owner(level, block);
        return frame == null ? null : frame.toWorld(point);
    }

    /**
     * Where a hit lies in the world. Sable gives a Ship block's hit, and an entity's in its plot (moved there with the Ship), in plot
     * coordinates; null for a plot hit whose Ship is gone.
     */
    default @Nullable Vec3 worldAt(Level level, HitResult hit) {
        BlockPos block = frameBlock(hit);
        return block == null ? hit.getLocation() : worldOf(level, block, hit.getLocation());
    }

    /** The block whose frame a hit's location is given in, or null for a miss. */
    static @Nullable BlockPos frameBlock(HitResult hit) {
        if (hit instanceof BlockHitResult b) return b.getType() == HitResult.Type.BLOCK ? b.getBlockPos() : null;
        return hit instanceof EntityHitResult ? BlockPos.containing(hit.getLocation()) : null;
    }

    static boolean enabled() {
        return TentaworksConfig.value(TentaworksConfig.SHIPS_GRIP);
    }

    final class Holder {
        private static final ShipFrames INSTANCE = ModList.get() != null && ModList.get().isLoaded("sable") ? sable() : NoShips.INSTANCE;

        private Holder() {
        }

        private static ShipFrames sable() {
            return SableShips.create();
        }
    }
}
