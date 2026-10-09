package dev.tentaworks.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** No Sable: the world is the only frame. */
public final class NoShips implements ShipFrames {
    public static final NoShips INSTANCE = new NoShips();

    private NoShips() {
    }

    @Override
    public List<Frame> near(Level level, AABB box) {
        return List.of();
    }

    @Override
    public Frame containing(Level level, BlockPos pos) {
        return null;
    }

    @Override
    public boolean isPlot(Level level, BlockPos pos) {
        return false;
    }

    @Override
    public List<Pose> recent(Frame frame) {
        return List.of(frame.pose());
    }

    @Override
    public boolean tracking(Entity entity, Frame frame) {
        return false;
    }

    @Override
    public void dropInherited(Entity entity) {
    }
}
