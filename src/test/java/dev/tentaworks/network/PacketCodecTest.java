package dev.tentaworks.network;

import dev.tentaworks.rig.Grip;
import dev.tentaworks.rig.RigSettings;
import dev.tentaworks.rig.RigSettings.ActivationMode;
import dev.tentaworks.rig.RigState;
import dev.tentaworks.rig.Tentacles;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The three packets' payloads survive a buffer write and read unchanged, non-finite values included: the server
 * checks them (CR2 Stows on a non-finite motion or Grip), so they must arrive as sent, not be cleaned on the way.
 */
class PacketCodecTest {
    private static final Grip WORLD = new Grip(new Vec3(12.5, 64.0, -3.25), new BlockPos(12, 63, -4), Direction.UP);
    private static final Grip ODD = new Grip(new Vec3(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY),
            new BlockPos(-30_000_000, -64, 29_999_999), Direction.WEST);
    private static final Grip NEG_ZERO = new Grip(new Vec3(-0.0, 0.0, -0.0), BlockPos.ZERO, Direction.DOWN);

    private static <T> T roundTrip(T value, BiConsumer<T, FriendlyByteBuf> write, Function<FriendlyByteBuf, T> read) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        write.accept(value, buf);
        T back = read.apply(buf);
        assertEquals(0, buf.readableBytes(), "bytes left after read");
        return back;
    }

    private static void assertTentacles(Tentacles expected, Tentacles actual) {
        assertArrayEquals(expected.grips(), actual.grips());
        assertArrayEquals(expected.phases(), actual.phases());
    }

    @Test
    void grip() {
        for (Grip g : new Grip[]{WORLD, ODD, NEG_ZERO}) assertEquals(g, roundTrip(g, Grip::write, Grip::read));
        for (Direction d : Direction.values()) {
            Grip g = new Grip(WORLD.point(), WORLD.block(), d);
            assertEquals(g, roundTrip(g, Grip::write, Grip::read));
        }
    }

    @Test
    void nonFiniteGripArrivesAsSent() {
        Vec3 back = roundTrip(ODD, Grip::write, Grip::read).point();
        assertEquals(Double.NaN, back.x);
        assertEquals(Double.POSITIVE_INFINITY, back.y);
        assertEquals(Double.NEGATIVE_INFINITY, back.z);
    }

    @Test
    void tentacles() {
        Tentacles t = new Tentacles(new Grip[]{WORLD, null, ODD, NEG_ZERO}, new byte[]{-1, -1, 0, Tentacles.phaseByte(0.5F)});
        assertTentacles(t, roundTrip(t, Tentacles::write, Tentacles::read));
        assertTentacles(Tentacles.NONE, roundTrip(Tentacles.NONE, Tentacles::write, Tentacles::read));
        Tentacles full = new Tentacles(new Grip[]{WORLD, WORLD, NEG_ZERO, ODD}, new byte[]{1, 2, 3, 4});
        assertTentacles(full, roundTrip(full, Tentacles::write, Tentacles::read));
    }

    @Test
    void hangingSlotDropsItsPhase() {
        Tentacles t = new Tentacles(new Grip[]{null, WORLD, null, null}, new byte[]{5, 6, 7, 8});
        Tentacles back = roundTrip(t, Tentacles::write, Tentacles::read);
        assertArrayEquals(new Grip[]{null, WORLD, null, null}, back.grips());
        assertArrayEquals(new byte[]{-1, 6, -1, -1}, back.phases());
    }

    @Test
    void rigSettings() {
        RigSettings[] all = {RigSettings.DEFAULT, new RigSettings(2.3F, 6.1F, ActivationMode.KEY),
                new RigSettings(Float.NaN, Float.NEGATIVE_INFINITY, ActivationMode.DOUBLE_JUMP),
                new RigSettings(-0.0F, Float.MAX_VALUE, ActivationMode.KEY)};
        for (RigSettings s : all) assertEquals(s, roundTrip(s, RigSettings::write, RigSettings::read));
    }

    @Test
    void settingsPacket() {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.CHEST}) {
            RigSettingsPacket p = new RigSettingsPacket(slot, new RigSettings(Float.NaN, 6.1F, ActivationMode.KEY));
            assertEquals(p, roundTrip(p, RigSettingsPacket::write, RigSettingsPacket::read));
        }
    }

    @Test
    void motionPacket() {
        Tentacles t = new Tentacles(new Grip[]{WORLD, null, ODD, null}, new byte[]{-1, -1, 9, -1});
        for (RigState state : RigState.values()) {
            RigMotionPacket p = new RigMotionPacket(state, new Vec3(Double.NaN, -0.0, Double.POSITIVE_INFINITY), t);
            RigMotionPacket back = roundTrip(p, RigMotionPacket::write, RigMotionPacket::read);
            assertEquals(p.state(), back.state());
            assertEquals(p.motion(), back.motion());
            assertTentacles(t, back.tentacles());
        }
    }

    @Test
    void statePacket() {
        Tentacles t = new Tentacles(new Grip[]{null, NEG_ZERO, null, WORLD}, new byte[]{-1, 0, -1, -1});
        for (RigState state : RigState.values()) {
            RigStatePacket p = new RigStatePacket(Integer.MAX_VALUE, state, t);
            RigStatePacket back = roundTrip(p, RigStatePacket::write, RigStatePacket::read);
            assertEquals(p.entityId(), back.entityId());
            assertEquals(p.state(), back.state());
            assertTentacles(t, back.tentacles());
        }
    }
}
