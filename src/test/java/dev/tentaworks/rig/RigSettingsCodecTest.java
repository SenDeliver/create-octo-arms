package dev.tentaworks.rig;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rig Settings saved by 1.x load unchanged with Far Use always; a Speed never set reads as the server's top Speed. */
class RigSettingsCodecTest {
    private static RigSettings decode(String json) {
        return RigSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void oldJsonWithoutFarUse() {
        RigSettings s = decode("{\"standing_height\": 2.0, \"speed\": 6.0, \"activation\": \"key\"}");
        assertEquals(2.0F, s.standingHeight());
        assertEquals(6.0F, s.speed());
        assertEquals(RigSettings.ActivationMode.KEY, s.activation());
        assertEquals(RigSettings.FarUseMode.ALWAYS, s.farUse());
    }

    @Test
    void speedDefaultIsTheTopSpeed() {
        assertEquals(RigSettings.maxSpeed(), decode("{}").clamped(4.0).speed());
        assertEquals(RigSettings.maxSpeed(), RigSettings.DEFAULT.clamped(4.0).speed());
        assertEquals(RigSettings.maxSpeed(), RigSettings.of(dev.tentaworks.registry.TentaworksItems.COPPER_TENTACLE_RIG.asStack()).speed());
    }

    @Test
    void anUnsetSpeedIsNotSaved() {
        JsonObject json = RigSettings.CODEC.encodeStart(JsonOps.INSTANCE, RigSettings.DEFAULT).getOrThrow().getAsJsonObject();
        assertFalse(json.has("speed"), json.toString());
        assertTrue(Float.isNaN(decode(json.toString()).speed()));
    }

    @Test
    void farUseRoundTrips() {
        for (RigSettings.FarUseMode m : RigSettings.FarUseMode.values()) {
            RigSettings s = new RigSettings(1.0F, 5.0F, RigSettings.ActivationMode.DOUBLE_JUMP, m);
            JsonObject json = RigSettings.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow().getAsJsonObject();
            if (m == RigSettings.FarUseMode.ALWAYS) assertFalse(json.has("far_use"), json.toString());
            else assertEquals(m.getSerializedName(), json.get("far_use").getAsString());
            assertEquals(s, decode(json.toString()));
            ByteBuf buf = Unpooled.buffer();
            RigSettings.STREAM_CODEC.encode(buf, s);
            assertEquals(s, RigSettings.STREAM_CODEC.decode(buf));
        }
        assertEquals("rig_out", RigSettings.FarUseMode.RIG_OUT.getSerializedName());
        assertEquals(RigSettings.FarUseMode.ALWAYS, decode("{\"far_use\": \"sideways\"}").farUse());
    }

    @Test
    void clampingKeepsFarUse() {
        RigSettings s = new RigSettings(30F, 99F, RigSettings.ActivationMode.KEY, RigSettings.FarUseMode.RIG_OUT).clamped(4.0);
        assertEquals(4.0F, s.standingHeight());
        assertEquals(RigSettings.maxSpeed(), s.speed());
        assertEquals(RigSettings.FarUseMode.RIG_OUT, s.farUse());
    }
}
