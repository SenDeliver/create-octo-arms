package dev.tentaworks.network;

import dev.tentaworks.TentaworksIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Wearer → server, the Grab key and attack while holding (adr/0007: the server decides, then answers with
 * {@link ActuatorStatePacket}). START: Grab entity {@code entityId}. SHIP: Ship Grab of the Ship owning plot block {@code block}
 * by its {@code face}. RELEASE: set the held target down where it is. THROW: fling it along {@code look}. PLACE: carry the held
 * mob to the set-down spot whose feet are at {@code look} and set it down there (the server checks the spot). DISTANCE: move the
 * hold {@code notches} × 0.5 blocks farther along the look (negative: nearer).
 */
public record RigGrabPacket(Kind kind, int entityId, BlockPos block, Direction face, Vec3 look, int notches) implements CustomPacketPayload {
    public static final Type<RigGrabPacket> TYPE = new Type<>(TentaworksIds.id("rig_grab"));
    public static final StreamCodec<FriendlyByteBuf, RigGrabPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeEnum(p.kind);
                buf.writeVarInt(p.entityId + 1);
                buf.writeBlockPos(p.block);
                buf.writeByte(p.face.get3DDataValue());
                buf.writeVec3(p.look);
                buf.writeVarInt(p.notches);
            },
            buf -> new RigGrabPacket(buf.readEnum(Kind.class), buf.readVarInt() - 1, buf.readBlockPos(), Direction.from3DDataValue(buf.readByte()),
                    buf.readVec3(), buf.readVarInt()));

    /** Wire order is frozen: new kinds go at the end. */
    public enum Kind {
        START, SHIP, RELEASE, THROW, PLACE, DISTANCE
    }

    public static RigGrabPacket start(int entityId) {
        return new RigGrabPacket(Kind.START, entityId, BlockPos.ZERO, Direction.UP, Vec3.ZERO, 0);
    }

    public static RigGrabPacket ship(BlockPos block, Direction face) {
        return new RigGrabPacket(Kind.SHIP, -1, block, face, Vec3.ZERO, 0);
    }

    public static RigGrabPacket release() {
        return new RigGrabPacket(Kind.RELEASE, -1, BlockPos.ZERO, Direction.UP, Vec3.ZERO, 0);
    }

    public static RigGrabPacket throwAlong(Vec3 look) {
        return new RigGrabPacket(Kind.THROW, -1, BlockPos.ZERO, Direction.UP, look, 0);
    }

    public static RigGrabPacket place(Vec3 feet) {
        return new RigGrabPacket(Kind.PLACE, -1, BlockPos.ZERO, Direction.UP, feet, 0);
    }

    public static RigGrabPacket distance(int notches) {
        return new RigGrabPacket(Kind.DISTANCE, -1, BlockPos.ZERO, Direction.UP, Vec3.ZERO, notches);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
