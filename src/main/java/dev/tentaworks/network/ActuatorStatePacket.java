package dev.tentaworks.network;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.HoldArm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Server → clients: what the Actuators of player {@code entityId} hold. {@code heldId} the held mob (-1 none), {@code heavy}
 * when both Actuators hold it, {@code ship} the Ship block held by {@code shipFace} (null none), {@code arm} which Actuator holds,
 * {@code distance} the hold distance along the look (blocks, 0 none), {@code overhead} a Ship held Overhead, {@code shipTangent} the
 * in-face direction of the held face the two tips grip along (its Frame, toward the wearer's right at the Grab). To the wearer after every
 * Grab request, with {@code refusal} the lang key of the action-bar line when it was refused (empty otherwise); to trackers on
 * every change and on start of tracking.
 */
public record ActuatorStatePacket(int entityId, int heldId, boolean heavy, @Nullable BlockPos ship, Direction shipFace, String refusal,
                                  HoldArm arm, float distance, boolean overhead, Direction shipTangent) implements CustomPacketPayload {
    public static final Type<ActuatorStatePacket> TYPE = new Type<>(TentaworksIds.id("actuator_state"));
    private static final int MAX_REFUSAL = 256;
    public static final StreamCodec<FriendlyByteBuf, ActuatorStatePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeVarInt(p.heldId + 1);
                buf.writeBoolean(p.heavy);
                buf.writeBoolean(p.ship != null);
                if (p.ship != null) buf.writeBlockPos(p.ship);
                buf.writeByte(p.shipFace.get3DDataValue());
                buf.writeUtf(p.refusal, MAX_REFUSAL);
                buf.writeByte(p.arm.ordinal());
                buf.writeFloat(p.distance);
                buf.writeBoolean(p.overhead);
                buf.writeByte(p.shipTangent.get3DDataValue());
            },
            buf -> {
                int entity = buf.readVarInt();
                int held = buf.readVarInt() - 1;
                boolean heavy = buf.readBoolean();
                BlockPos ship = buf.readBoolean() ? buf.readBlockPos() : null;
                Direction face = Direction.from3DDataValue(buf.readByte());
                String refusal = buf.readUtf(MAX_REFUSAL);
                return new ActuatorStatePacket(entity, held, heavy, ship, face, refusal, HoldArm.byId(buf.readByte()),
                        buf.readFloat(), buf.readBoolean(), Direction.from3DDataValue(buf.readByte()));
            });

    public static ActuatorStatePacket empty(int entityId, String refusal) {
        return new ActuatorStatePacket(entityId, -1, false, null, Direction.UP, refusal, HoldArm.NONE, 0, false, Direction.NORTH);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
