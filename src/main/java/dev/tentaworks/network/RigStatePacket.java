package dev.tentaworks.network;

import dev.tentaworks.rig.RigState;
import dev.tentaworks.rig.Tentacles;
import net.minecraft.network.FriendlyByteBuf;

/** Server → clients: a Rig's state and its validated Tentacles (Grip + Step phase per slot), relayed every tick it moves. */
public record RigStatePacket(int entityId, RigState state, Tentacles tentacles) {
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeEnum(state);
        tentacles.write(buf);
    }

    public static RigStatePacket read(FriendlyByteBuf buf) {
        return new RigStatePacket(buf.readVarInt(), buf.readEnum(RigState.class), Tentacles.read(buf));
    }
}
