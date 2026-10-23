package dev.tentaworks.network;

import dev.tentaworks.rig.RigState;
import dev.tentaworks.rig.Tentacles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

public record RigMotionPacket(RigState state, Vec3 motion, Tentacles tentacles) {
    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(state);
        buf.writeDouble(motion.x);
        buf.writeDouble(motion.y);
        buf.writeDouble(motion.z);
        tentacles.write(buf);
    }

    public static RigMotionPacket read(FriendlyByteBuf buf) {
        return new RigMotionPacket(buf.readEnum(RigState.class), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), Tentacles.read(buf));
    }
}
