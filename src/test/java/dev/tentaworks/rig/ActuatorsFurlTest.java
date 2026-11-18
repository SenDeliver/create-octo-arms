package dev.tentaworks.rig;

import com.mojang.serialization.JsonOps;
import dev.tentaworks.network.ActuatorFurlPacket;
import dev.tentaworks.registry.TentaworksDataComponents;
import dev.tentaworks.registry.TentaworksItems;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Furled Actuators: on the Rig item in place, saved and synced, kept by copies, off for every Actuator use, and still the same worn Rig. */
class ActuatorsFurlTest {
    private static ItemStack fitted() {
        return Actuators.fit(TentaworksItems.COPPER_TENTACLE_RIG.asStack());
    }

    @Test
    void furlIsInPlaceAndOnlyOnFittedActuators() {
        ItemStack rig = fitted();
        assertTrue(Actuators.on(rig));
        assertFalse(Actuators.furled(rig));
        assertTrue(Actuators.furl(rig, true));
        assertTrue(Actuators.furled(rig));
        assertFalse(Actuators.on(rig));
        assertTrue(Actuators.fitted(rig), "Furled Actuators are still fitted");
        assertTrue(Actuators.furl(rig, false));
        assertTrue(Actuators.on(rig));
        assertFalse(rig.has(TentaworksDataComponents.ACTUATORS_FURLED.get()), "unfurling removes the component");

        ItemStack bare = TentaworksItems.COPPER_TENTACLE_RIG.asStack();
        assertFalse(Actuators.furl(bare, true));
        assertFalse(Actuators.furled(bare));
        assertFalse(Actuators.on(bare));
        assertFalse(Actuators.on(ItemStack.EMPTY));
    }

    @Test
    void aFurledComponentWithoutActuatorsIsNotFurled() {
        ItemStack bare = TentaworksItems.COPPER_TENTACLE_RIG.asStack();
        bare.set(TentaworksDataComponents.ACTUATORS_FURLED.get(), Unit.INSTANCE);
        assertFalse(Actuators.furled(bare));
        assertFalse(Actuators.on(bare));
    }

    @Test
    void savedSyncedAndCopied() {
        assertNotNull(TentaworksDataComponents.ACTUATORS_FURLED.get().codec());
        assertNotNull(TentaworksDataComponents.ACTUATORS_FURLED.get().streamCodec());
        ItemStack rig = fitted();
        Actuators.furl(rig, true);
        var json = ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, rig).getOrThrow();
        assertTrue(json.toString().contains("tentaworks:actuators_furled"), json.toString());
        assertTrue(Actuators.furled(ItemStack.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()), "survives a relog");
        assertTrue(Actuators.furled(rig.copy()));
        assertTrue(Actuators.furled(rig.transmuteCopy(TentaworksItems.NETHERITE_TENTACLE_RIG.get(), 1)), "smithing keeps it");
    }

    @Test
    void furlingIsTheSameWornRig() {
        ItemStack a = fitted(), b = a.copy();
        Actuators.furl(b, true);
        assertTrue(WornRig.same(new WornRig(a, RigSlot.CHEST), new WornRig(b, RigSlot.CHEST)));
        assertTrue(WornRig.same(new WornRig(b, RigSlot.BACK), new WornRig(a, RigSlot.BACK)));
    }

    @Test
    void noLiftWhenFurled() {
        ItemStack rig = fitted();
        Actuators.furl(rig, true);
        assertFalse(FarUse.lifts(Actuators.on(rig), RigSettings.FarUseMode.ALWAYS, true, false, false, false));
        Actuators.furl(rig, false);
        assertTrue(FarUse.lifts(Actuators.on(rig), RigSettings.FarUseMode.ALWAYS, true, false, false, false));
    }

    @Test
    void packetRoundTrip() {
        for (boolean furled : new boolean[]{false, true}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            ActuatorFurlPacket.STREAM_CODEC.encode(buf, new ActuatorFurlPacket(furled));
            assertEquals(new ActuatorFurlPacket(furled), ActuatorFurlPacket.STREAM_CODEC.decode(buf));
            assertEquals(0, buf.readableBytes());
        }
    }
}
