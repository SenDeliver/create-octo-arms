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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The round 3 packet fields survive the wire: the Far Use hand, the hold arm and distance, the wheel notches and set-down feet, Overhead, the grip tangent, a Turn, the tips on a Ship and its build. */
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
            ActuatorStatePacket p = new ActuatorStatePacket(3, 12, arm == HoldArm.BOTH, null, Direction.UP, "", arm, 2.55F, false, Direction.NORTH,
                    ActuatorStatePacket.Tip.NONE, ActuatorStatePacket.Tip.NONE, List.of(), arm == HoldArm.BOTH ? 0.6 : 1);
            assertEquals(p, trip(ActuatorStatePacket.STREAM_CODEC, p));
        }
        ActuatorStatePacket ship = new ActuatorStatePacket(3, -1, true, new BlockPos(5, -59, 2), Direction.WEST, "", HoldArm.BOTH, 1.5F, false, Direction.SOUTH,
                new ActuatorStatePacket.Tip(new Vec3(0.5, 0.5, -0.25), new Vec3(0.5, 0.5, -0.25), -1),
                new ActuatorStatePacket.Tip(new Vec3(1.0, 1.75, 0.5), new Vec3(0.5, 0.5, 0.75), 3),
                List.of(new UUID(1, 2), new UUID(-3, 4), new UUID(5, -6)), 0.6 * 0.76);
        assertEquals(ship, trip(ActuatorStatePacket.STREAM_CODEC, ship));
        ActuatorStatePacket overhead = new ActuatorStatePacket(3, -1, true, new BlockPos(5, -59, 2), Direction.UP, "", HoldArm.BOTH, 6.5F, true, Direction.EAST,
                new ActuatorStatePacket.Tip(new Vec3(-0.5, 0, 0.5), new Vec3(0.5, 0, 0.5), 0), ActuatorStatePacket.Tip.NONE, List.of(new UUID(7, 8)), 0.15);
        assertEquals(overhead, trip(ActuatorStatePacket.STREAM_CODEC, overhead));
        ActuatorStatePacket.Tip back = trip(ActuatorStatePacket.STREAM_CODEC, ship).right();
        assertEquals(3, back.reach(), "the right tip reaching, 3 ticks in");
        assertEquals(new Vec3(0.5, 0.5, 0.75), back.from(), "from where it held");
        assertEquals(List.of(new UUID(1, 2), new UUID(-3, 4), new UUID(5, -6)), trip(ActuatorStatePacket.STREAM_CODEC, ship).build(), "the build, held Ship first");
        assertEquals(0.6 * 0.76, trip(ActuatorStatePacket.STREAM_CODEC, ship).speed(), "the speed factor, exact");
    }

    @Test
    void grabCarriesTheNotchesAndTheFeet() {
        for (int n : new int[] {-10, -1, 1, 3, 10}) {
            RigGrabPacket p = RigGrabPacket.distance(n);
            assertEquals(p, trip(RigGrabPacket.STREAM_CODEC, p));
        }
        RigGrabPacket p = RigGrabPacket.place(new Vec3(11204.0, -60.0, 113.5));
        assertEquals(p, trip(RigGrabPacket.STREAM_CODEC, p));
        RigGrabPacket turn = RigGrabPacket.turn(15.0F, -7.5F);
        assertEquals(turn, trip(RigGrabPacket.STREAM_CODEC, turn));
        assertEquals(RigGrabPacket.Kind.TURN, RigGrabPacket.Kind.values()[RigGrabPacket.Kind.values().length - 1], "new kinds at the end");
    }
}
