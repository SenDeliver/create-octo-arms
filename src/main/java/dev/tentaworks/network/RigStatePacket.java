package dev.tentaworks.network;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.RigState;
import dev.tentaworks.rig.Tentacles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → clients: a Rig's state and its validated Tentacles (Grip + Step phase per slot), relayed every tick it moves. */
public record RigStatePacket(int entityId, RigState state, Tentacles tentacles) implements CustomPacketPayload {
    public static final Type<RigStatePacket> TYPE = new Type<>(TentaworksIds.id("rig_state"));
    public static final StreamCodec<FriendlyByteBuf, RigStatePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeEnum(p.state);
                Tentacles.STREAM_CODEC.encode(buf, p.tentacles);
            },
            buf -> new RigStatePacket(buf.readVarInt(), buf.readEnum(RigState.class), Tentacles.STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
