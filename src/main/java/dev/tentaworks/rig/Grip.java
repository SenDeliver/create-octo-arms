package dev.tentaworks.rig;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * A Tentacle tip on one face of one block. The block is explicit: points on a face edge sit on the boundary of two blocks.
 */
public record Grip(Vec3 point, BlockPos block, Direction face) {
    public static final StreamCodec<ByteBuf, Grip> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, g -> g.point.x,
            ByteBufCodecs.DOUBLE, g -> g.point.y,
            ByteBufCodecs.DOUBLE, g -> g.point.z,
            BlockPos.STREAM_CODEC, Grip::block,
            Direction.STREAM_CODEC, Grip::face,
            (x, y, z, b, f) -> new Grip(new Vec3(x, y, z), b, f));
}
