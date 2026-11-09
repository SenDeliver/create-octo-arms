package dev.tentaworks.rig;

import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllItems;
import dev.tentaworks.registry.TentaworksDataComponents;
import dev.tentaworks.registry.TentaworksItems;
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
    void strikeReachPerTier() {
        assertEquals(3.5, Actuators.strikeReach(TentaworksItems.COPPER_TENTACLE_RIG.asStack()), 1.0E-9);
        assertEquals(5.0, Actuators.strikeReach(TentaworksItems.NETHERITE_TENTACLE_RIG.asStack()), 1.0E-9);
        assertEquals(0, Actuators.strikeReach(ItemStack.EMPTY));
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
