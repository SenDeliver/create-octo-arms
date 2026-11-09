package dev.tentaworks.rig;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rig_event criterion (event name, optional number range), the advancement files that use it, and the Hang rule. */
class RigEventsTest {
    private static RigEventTrigger.Instance at(String event, double min) {
        return new RigEventTrigger.Instance(Optional.empty(), event, MinMaxBounds.Doubles.atLeast(min));
    }

    private static Grip grip(double x, double y, double z, Direction face) {
        return new Grip(new Vec3(x, y, z), BlockPos.containing(x, y, z), face);
    }

    @Test
    void eventNameAndThreshold() {
        RigEventTrigger.Instance delivery = at(RigEvents.THROW_DISTANCE, 20);
        assertTrue(delivery.matches(RigEvents.THROW_DISTANCE, 20));
        assertTrue(delivery.matches(RigEvents.THROW_DISTANCE, 31.5));
        assertFalse(delivery.matches(RigEvents.THROW_DISTANCE, 19.99));
        assertFalse(delivery.matches(RigEvents.IMPACT, 50), "another event with a big number");
        RigEventTrigger.Instance any = new RigEventTrigger.Instance(Optional.empty(), RigEvents.GRAB_BLAZE, MinMaxBounds.Doubles.ANY);
        assertTrue(any.matches(RigEvents.GRAB_BLAZE, 1), "no number: any value");
    }

    /** Each rig_event advancement decodes with the criterion's own codec and fires on its event at its threshold only. */
    @Test
    void advancementFiles() throws IOException {
        Object[][] files = {{"hang_in_there", RigEvents.HANG, 60.0}, {"spider_man", RigEvents.SPIDER_KILL, 1.0}, {"power_of_the_sun", RigEvents.GRAB_BLAZE, 1.0},
                {"fridge_mover", RigEvents.SHIP_GRAB, 50.0}, {"special_delivery", RigEvents.THROW_DISTANCE, 20.0}};
        for (Object[] f : files) {
            JsonObject json = read("data/tentaworks/advancement/" + f[0] + ".json");
            JsonObject criterion = json.getAsJsonObject("criteria").entrySet().iterator().next().getValue().getAsJsonObject();
            assertEquals("tentaworks:rig_event", criterion.get("trigger").getAsString(), (String) f[0]);
            RigEventTrigger.Instance i = RigEventTrigger.Instance.CODEC.parse(JsonOps.INSTANCE, criterion.get("conditions")).getOrThrow();
            double n = (double) f[2];
            assertTrue(i.matches((String) f[1], n), (String) f[0]);
            if (n > 1) assertFalse(i.matches((String) f[1], n - 0.5), f[0] + " below its threshold");
        }
        assertEquals("tentaworks:fitted", read("data/tentaworks/advancement/special_delivery.json").get("parent").getAsString());
        assertTrue(read("data/tentaworks/advancement/intelligence_is_a_privilege.json").toString().contains("tentaworks:deft"));
    }

    private JsonObject read(String path) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void hangingNeedsTwoGripsAboveAndOneOnACeiling() {
        double body = 10;
        Grip ceilingA = grip(0, 12, 0, Direction.DOWN), ceilingB = grip(1, 12, 1, Direction.DOWN);
        Grip wallBeside = grip(2, 10.5, 0, Direction.WEST), floor = grip(0, 8, 0, Direction.UP);
        assertTrue(RigEvents.hanging(body, List.of(ceilingA, ceilingB)));
        assertTrue(RigEvents.hanging(body, List.of(ceilingA, wallBeside)), "a wall beside the ceiling still hangs");
        assertFalse(RigEvents.hanging(body, List.of(ceilingA)), "one Grip is not Held");
        assertFalse(RigEvents.hanging(body, List.of(ceilingA, ceilingB, floor)), "a foot on the floor");
        assertFalse(RigEvents.hanging(body, List.of(wallBeside, grip(2, 11, 1, Direction.WEST))), "a wall only, no ceiling");
    }
}
