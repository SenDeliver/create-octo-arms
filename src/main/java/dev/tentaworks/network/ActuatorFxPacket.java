package dev.tentaworks.network;

import dev.tentaworks.TentaworksIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → clients, the moments the Actuators' feedback follows. THROW: player {@code entityId} Threw what it held along
 * {@code at} (the throw velocity, blocks per tick, {@code speed} its length); to trackers and the wearer. IMPACT: Thrown entity
 * {@code entityId} made its Impact at {@code at} with {@code speed} blocks per tick; to its trackers.
 */
public record ActuatorFxPacket(Kind kind, int entityId, Vec3 at, double speed) implements CustomPacketPayload {
    public static final Type<ActuatorFxPacket> TYPE = new Type<>(TentaworksIds.id("actuator_fx"));
    public static final StreamCodec<FriendlyByteBuf, ActuatorFxPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind.ordinal());
                buf.writeVarInt(p.entityId);
                buf.writeVec3(p.at);
                buf.writeFloat((float) p.speed);
            },
            buf -> new ActuatorFxPacket(Kind.byId(buf.readByte()), buf.readVarInt(), buf.readVec3(), buf.readFloat()));

    /** Wire order is frozen: new kinds go at the end. */
    public enum Kind {
        THROW, IMPACT;

        static Kind byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : THROW;
        }
    }

    public static ActuatorFxPacket thrown(int wearerId, Vec3 velocity) {
        return new ActuatorFxPacket(Kind.THROW, wearerId, velocity, velocity.length());
    }

    public static ActuatorFxPacket impact(int thrownId, Vec3 at, double speed) {
        return new ActuatorFxPacket(Kind.IMPACT, thrownId, at, speed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
