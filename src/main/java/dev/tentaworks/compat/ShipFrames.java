package dev.tentaworks.compat;

import dev.tentaworks.compat.sable.SableShips;
import dev.tentaworks.config.TentaworksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

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

    /** Whether a position is in Sable's plot grid (Ship space), whatever the config. */
    boolean isPlot(Level level, BlockPos pos);

    /**
     * Poses a Grip on this Ship may be checked against: the current and last pose, plus on the server the poses of the
     * last few server ticks (client Grips are built from a lagging pose).
     */
    List<Pose> recent(Frame frame);

    /** World point of a point given in the frame of {@code block}: unchanged for world blocks or when the Ship is gone. */
    default Vec3 toWorld(Level level, BlockPos block, Vec3 point) {
        if (!isPlot(level, block)) return point;
        Frame frame = containing(level, block);
        return frame == null ? point : frame.toWorld(point);
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
