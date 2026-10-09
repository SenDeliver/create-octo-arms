package dev.tentaworks.network;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.RigState;
import dev.tentaworks.rig.Tentacles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record RigMotionPacket(RigState state, Vec3 motion, Tentacles tentacles) implements CustomPacketPayload {
    public static final Type<RigMotionPacket> TYPE = new Type<>(TentaworksIds.id("rig_motion"));
    public static final StreamCodec<FriendlyByteBuf, RigMotionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeEnum(p.state);
                buf.writeVec3(p.motion);
                Tentacles.STREAM_CODEC.encode(buf, p.tentacles);
            },
            buf -> new RigMotionPacket(buf.readEnum(RigState.class), buf.readVec3(), Tentacles.STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
