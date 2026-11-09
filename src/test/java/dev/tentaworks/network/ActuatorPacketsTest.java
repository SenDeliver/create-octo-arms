package dev.tentaworks.network;

import dev.tentaworks.rig.HoldArm;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The round 3 packet fields survive the wire: the Far Use hand, the hold arm and distance, the wheel notches and set-down feet. */
class ActuatorPacketsTest {
    private static <T> T trip(StreamCodec<FriendlyByteBuf, T> codec, T p) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, p);
        T back = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "every byte read");
        return back;
    }

    @Test
    void farUseCarriesTheHand() {
        for (InteractionHand hand : InteractionHand.values()) {
            FarUsePacket p = new FarUsePacket(7, new Vec3(1.5, -60, 3.25), FarUsePacket.Kind.ENTITY, 10, hand);
            assertEquals(p, trip(FarUsePacket.STREAM_CODEC, p));
        }
    }

    @Test
    void actuatorStateCarriesTheArmAndDistance() {
        for (HoldArm arm : HoldArm.values()) {
            ActuatorStatePacket p = new ActuatorStatePacket(3, 12, arm == HoldArm.BOTH, null, Direction.UP, "", arm, 2.55F);
            assertEquals(p, trip(ActuatorStatePacket.STREAM_CODEC, p));
        }
        ActuatorStatePacket ship = new ActuatorStatePacket(3, -1, true, new BlockPos(5, -59, 2), Direction.WEST, "", HoldArm.BOTH, 1.5F);
        assertEquals(ship, trip(ActuatorStatePacket.STREAM_CODEC, ship));
    }

    @Test
    void grabCarriesTheNotchesAndTheFeet() {
        for (int n : new int[] {-10, -1, 1, 3, 10}) {
            RigGrabPacket p = RigGrabPacket.distance(n);
            assertEquals(p, trip(RigGrabPacket.STREAM_CODEC, p));
        }
        RigGrabPacket p = RigGrabPacket.place(new Vec3(11204.0, -60.0, 113.5));
        assertEquals(p, trip(RigGrabPacket.STREAM_CODEC, p));
    }
}
