package dev.tentaworks.rig;

import dev.tentaworks.compat.ShipFrames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Tentacle tip on one face of one block. The block is explicit: points on a face edge sit on the boundary of two blocks.
 * On a Ship, point and block are in the Ship's plot coords (the block names the Ship); see {@link #world}.
 */
public record Grip(Vec3 point, BlockPos block, Direction face) {
    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(point.x);
        buf.writeDouble(point.y);
        buf.writeDouble(point.z);
        buf.writeBlockPos(block);
        buf.writeVarInt(face.get3DDataValue());
    }

    public static Grip read(FriendlyByteBuf buf) {
        Vec3 point = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        return new Grip(point, buf.readBlockPos(), Direction.from3DDataValue(buf.readVarInt()));
    }

    /** The point in world coords under the Ship's current pose; unchanged for a world Grip or when the Ship is gone. */
    public Vec3 world(Level level) {
        return ShipFrames.get().toWorld(level, block, point);
    }
}
