package dev.tentaworks.rig;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.tentaworks.TentaworksIds;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every Rig enchantment has its data file (Rig items only, chest slot) and sits in the enchanting table and non-treasure tags. */
class RigEnchantDataTest {
    private static final List<String> OURS = List.of(TentaworksIds.FAR_REACH, TentaworksIds.THRIFT, TentaworksIds.SWIFT, TentaworksIds.SILENT_STEP);

    /** Every copy on the classpath: the vanilla jar carries tag files of the same path as ours. */
    private static List<JsonObject> json(String path) throws IOException {
        List<JsonObject> out = new ArrayList<>();
        for (URL url : Collections.list(RigEnchantDataTest.class.getClassLoader().getResources(path))) {
            try (var in = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8)) {
                out.add(JsonParser.parseReader(in).getAsJsonObject());
            }
        }
        assertFalse(out.isEmpty(), path);
        return out;
    }

    @Test
    void dataFiles() throws IOException {
        for (String id : OURS) {
            JsonObject e = json("data/tentaworks/enchantment/" + id + ".json").get(0);
            assertEquals("#tentaworks:" + TentaworksIds.RIG_ENCHANTABLE, e.get("supported_items").getAsString(), id);
            assertEquals("#tentaworks:" + TentaworksIds.RIG_ENCHANTABLE, e.get("primary_items").getAsString(), id);
            assertEquals("chest", e.getAsJsonArray("slots").get(0).getAsString(), id);
            assertEquals("enchantment.tentaworks." + id, e.getAsJsonObject("description").get("translate").getAsString(), id);
            assertTrue(e.get("max_level").getAsInt() >= 1, id);
        }
        assertEquals(1, json("data/tentaworks/enchantment/silent_step.json").get(0).get("max_level").getAsInt());
    }

    @Test
    void vanillaTags() throws IOException {
        for (String tag : new String[]{"in_enchanting_table", "non_treasure"}) {
            boolean ours = false;
            for (JsonObject o : json("data/minecraft/tags/enchantment/" + tag + ".json")) {
                JsonArray values = o.getAsJsonArray("values");
                if (!values.contains(new JsonPrimitive("tentaworks:" + TentaworksIds.FAR_REACH))) continue;
                ours = true;
                for (String id : OURS) assertTrue(values.contains(new JsonPrimitive("tentaworks:" + id)), id + " in " + tag);
            }
            assertTrue(ours, "our " + tag + " tag file on the classpath");
        }
    }
}
