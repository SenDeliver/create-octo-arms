package dev.tentaworks.network;

import dev.tentaworks.TentaworksIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Server → the wearer and trackers: player {@code entityId} did a Far Use at {@code target} with {@code hand}, so that hand's
 * Actuator reaches there for {@code ttl} ticks. Sent on each accepted far action, at most one per 2 ticks per wearer, and again before {@code ttl}
 * runs out while a far block is mined.
 */
public record FarUsePacket(int entityId, Vec3 target, Kind kind, int ttl, InteractionHand hand) implements CustomPacketPayload {
    public static final Type<FarUsePacket> TYPE = new Type<>(TentaworksIds.id("far_use"));
    public static final StreamCodec<FriendlyByteBuf, FarUsePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeVec3(p.target);
                buf.writeByte(p.kind.ordinal());
                buf.writeVarInt(p.ttl);
                buf.writeBoolean(p.hand == InteractionHand.OFF_HAND);
            },
            buf -> new FarUsePacket(buf.readVarInt(), buf.readVec3(), Kind.byId(buf.readByte()), buf.readVarInt(),
                    buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND));

    /** Wire order is frozen: new kinds go at the end. */
    public enum Kind {
        BLOCK, ENTITY;

        static Kind byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : BLOCK;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
