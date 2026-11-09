package dev.tentaworks.rig;

import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllItems;
import dev.tentaworks.registry.TentaworksDataComponents;
import dev.tentaworks.registry.TentaworksItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Actuators are a component on the Rig: set, read, saved, synced, and kept by every copy a Rig goes through. */
class ActuatorsComponentTest {
    @Test
    void fitSetsTheComponentOnACopy() {
        ItemStack rig = TentaworksItems.COPPER_TENTACLE_RIG.asStack();
        ItemStack fitted = Actuators.fit(rig);
        assertFalse(Actuators.fitted(rig));
        assertTrue(Actuators.fitted(fitted));
        assertEquals(1, fitted.getCount());
    }

    @Test
    void onlyARigIsFitted() {
        ItemStack tank = AllItems.COPPER_BACKTANK.asStack();
        assertFalse(Actuators.fitted(Actuators.fit(tank)));
    }

    @Test
    void persistentAndSynced() {
        assertNotNull(TentaworksDataComponents.ACTUATORS.get().codec());
        assertNotNull(TentaworksDataComponents.ACTUATORS.get().streamCodec());
    }

    @Test
    void savedAndLoaded() {
        ItemStack fitted = Actuators.fit(TentaworksItems.NETHERITE_TENTACLE_RIG.asStack());
        var json = ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, fitted).getOrThrow();
        assertTrue(json.toString().contains("tentaworks:actuators"), json.toString());
        assertTrue(Actuators.fitted(ItemStack.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()));
    }

    @Test
    void smithingAndTheRigBlockKeepIt() {
        ItemStack fitted = Actuators.fit(TentaworksItems.COPPER_TENTACLE_RIG.asStack());
        assertTrue(Actuators.fitted(fitted.transmuteCopy(TentaworksItems.NETHERITE_TENTACLE_RIG.get(), 1)));
        ItemStack pickedUp = new ItemStack(TentaworksItems.COPPER_TENTACLE_RIG.get().builtInRegistryHolder(), 1, fitted.getComponentsPatch());
        assertTrue(Actuators.fitted(pickedUp));
    }

    @Test
    void aFittedRigIsAnOctoRig() {
        ItemStack copper = TentaworksItems.COPPER_TENTACLE_RIG.asStack();
        assertEquals("item.tentaworks.copper_tentacle_rig", key(copper.getHoverName()));
        assertEquals("item.tentaworks.copper_octo_rig", key(Actuators.fit(copper).getHoverName()));
        assertEquals("item.tentaworks.netherite_octo_rig", key(Actuators.fit(TentaworksItems.NETHERITE_TENTACLE_RIG.asStack()).getHoverName()));
        ItemStack named = Actuators.fit(copper);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Doc's"));
        assertEquals("Doc's", named.getHoverName().getString(), "an anvil name still wins");
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    @Test
    void farUseReachIsTheRigReach() {
        ItemStack copper = Actuators.fit(TentaworksItems.COPPER_TENTACLE_RIG.asStack());
        ItemStack netherite = Actuators.fit(TentaworksItems.NETHERITE_TENTACLE_RIG.asStack());
        assertEquals(5.0, TentaworksItems.COPPER_TENTACLE_RIG.get().reach(copper), 1.0E-9, "a Copper Rig reaches half a block past a bare hand");
        assertEquals(7.0, TentaworksItems.NETHERITE_TENTACLE_RIG.get().reach(netherite), 1.0E-9);
    }

    @Test
    void actuatorAirFollowsTierAndThrift() {
        ItemStack copper = TentaworksItems.COPPER_TENTACLE_RIG.asStack();
        ItemStack netherite = TentaworksItems.NETHERITE_TENTACLE_RIG.asStack();
        assertEquals(0.5, RigAir.farUseCost(copper), 1.0E-9);
        assertEquals(0.25, RigAir.farUseCost(netherite), 1.0E-9);
        assertEquals(1.0 / 40, RigAir.grabPerTick(copper), 1.0E-9);
        assertEquals(10.0, RigAir.throwCost(copper), 1.0E-9);
        assertEquals(5.0, RigAir.throwCost(netherite), 1.0E-9);
    }
}
