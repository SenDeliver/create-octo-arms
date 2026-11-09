package dev.tentaworks.network;

import dev.tentaworks.TentaworksIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Server → clients: what the Actuators of player {@code entityId} hold. {@code heldId} the held mob (-1 none), {@code heavy}
 * when both Actuators hold it, {@code ship} the Ship block held by {@code shipFace} (null none). To the wearer after every
 * Grab request, with {@code refusal} the lang key of the action-bar line when it was refused (empty otherwise); to trackers on
 * every change and on start of tracking.
 */
public record ActuatorStatePacket(int entityId, int heldId, boolean heavy, @Nullable BlockPos ship, Direction shipFace, String refusal)
        implements CustomPacketPayload {
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
            },
            buf -> {
                int entity = buf.readVarInt();
                int held = buf.readVarInt() - 1;
                boolean heavy = buf.readBoolean();
                BlockPos ship = buf.readBoolean() ? buf.readBlockPos() : null;
                return new ActuatorStatePacket(entity, held, heavy, ship, Direction.from3DDataValue(buf.readByte()), buf.readUtf(MAX_REFUSAL));
            });

    public static ActuatorStatePacket empty(int entityId, String refusal) {
        return new ActuatorStatePacket(entityId, -1, false, null, Direction.UP, refusal);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
