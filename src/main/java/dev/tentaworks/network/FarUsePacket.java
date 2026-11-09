package dev.tentaworks.network;

import dev.tentaworks.TentaworksIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → trackers (not the wearer, who draws its own): player {@code entityId} did a Far Use at {@code target}, so its Right
 * Actuator reaches there for {@code ttl} ticks. Throttled by the sender.
 */
public record FarUsePacket(int entityId, Vec3 target, Kind kind, int ttl) implements CustomPacketPayload {
    public static final Type<FarUsePacket> TYPE = new Type<>(TentaworksIds.id("far_use"));
    public static final StreamCodec<FriendlyByteBuf, FarUsePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeVec3(p.target);
                buf.writeByte(p.kind.ordinal());
                buf.writeVarInt(p.ttl);
            },
            buf -> new FarUsePacket(buf.readVarInt(), buf.readVec3(), Kind.byId(buf.readByte()), buf.readVarInt()));

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
