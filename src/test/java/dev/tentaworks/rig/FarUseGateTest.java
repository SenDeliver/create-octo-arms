package dev.tentaworks.rig;

import dev.tentaworks.registry.TentaworksDataComponents;
import dev.tentaworks.registry.TentaworksItems;
import dev.tentaworks.registry.TentaworksLang;
import dev.tentaworks.rig.FarUse.Far;
import dev.tentaworks.rig.RigSettings.FarUseMode;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Far Use: when the Right Actuator lends its Reach (fitted, Rig Setting, Extendo Grip, heavy hold, limp), how far it lifts the
 * ranges, what counts as past arm's length, and how often a far action pays Air or is told to the clients.
 */
class FarUseGateTest {
    private static final double VANILLA_BLOCK = 4.5;
    private static final double VANILLA_ENTITY = 3.0;

    @Test
    void settingTimesFittedTimesRigOut() {
        for (FarUseMode mode : FarUseMode.values())
            for (boolean fitted : new boolean[]{false, true})
                for (boolean rigOut : new boolean[]{false, true}) {
                    boolean expected = fitted && (mode == FarUseMode.ALWAYS || mode == FarUseMode.RIG_OUT && rigOut);
                    assertEquals(expected, FarUse.lifts(fitted, mode, rigOut, false, false, false), mode + " fitted=" + fitted + " rigOut=" + rigOut);
                }
    }

    @Test
    void anExtendoGripInHandLiftsNothing() {
        for (FarUseMode mode : FarUseMode.values())
            for (boolean rigOut : new boolean[]{false, true}) assertFalse(FarUse.lifts(true, mode, rigOut, true, false, false));
    }

    @Test
    void aHeavyHoldPausesFarUse() {
        assertFalse(FarUse.lifts(true, FarUseMode.ALWAYS, true, false, true, false));
        assertFalse(FarUse.lifts(true, FarUseMode.RIG_OUT, true, false, true, false));
        assertTrue(FarUse.lifts(true, FarUseMode.ALWAYS, true, false, false, false));
    }

    @Test
    void limpActuatorsLiftNothing() {
        assertFalse(FarUse.lifts(true, FarUseMode.ALWAYS, false, false, false, true));
        assertFalse(FarUse.lifts(true, FarUseMode.RIG_OUT, true, false, false, true));
    }

    @Test
    void realStacks() {
        ItemStack plain = TentaworksItems.COPPER_TENTACLE_RIG.asStack();
        ItemStack fitted = Actuators.fit(plain);
        assertFalse(FarUse.lifts(Actuators.fitted(plain), RigSettings.of(plain).farUse(), true, false, false, false));
        assertTrue(FarUse.lifts(Actuators.fitted(fitted), RigSettings.of(fitted).farUse(), false, false, false, false));
        ItemStack off = fitted.copy();
        off.set(TentaworksDataComponents.RIG_SETTINGS.get(),
                new RigSettings(1.5F, RigSettings.UNSET_SPEED, RigSettings.ActivationMode.DOUBLE_JUMP, FarUseMode.OFF));
        assertFalse(FarUse.lifts(Actuators.fitted(off), RigSettings.of(off).farUse(), true, false, false, false));
        assertFalse(FarUse.lifts(Actuators.fitted(ItemStack.EMPTY), FarUseMode.ALWAYS, true, false, false, false));
    }

    @Test
    void theLiftRaisesToTheTargetAndNeverLowers() {
        assertEquals(2.5, FarUse.amount(7.0, VANILLA_BLOCK), 1.0E-9);
        assertEquals(4.0, FarUse.amount(7.0, VANILLA_ENTITY), 1.0E-9, "mobs and players at the full Reach too");
        assertEquals(0.5, FarUse.amount(5.0, VANILLA_BLOCK), 1.0E-9, "Copper Reach 5: half a block past a bare hand on blocks");
        assertEquals(2.0, FarUse.amount(5.0, VANILLA_ENTITY), 1.0E-9, "and 2 blocks on mobs");
        assertEquals(0.0, FarUse.amount(4.0, VANILLA_BLOCK), "a Reach under vanilla's 4.5 lifts nothing");
        assertEquals(0.0, FarUse.amount(3.5, 5.0), "a range already longer (creative, other mods) stays as it is");
        assertEquals(0.0, FarUse.amount(0, VANILLA_BLOCK));
    }

    @Test
    void pastArmsLengthIsStrictlyFartherThanTheUnliftedRange() {
        assertTrue(FarUse.beyond(3.2 * 3.2, VANILLA_ENTITY));
        assertFalse(FarUse.beyond(2.9 * 2.9, VANILLA_ENTITY));
        assertFalse(FarUse.beyond(3.0 * 3.0, VANILLA_ENTITY));
        assertTrue(FarUse.beyond(5.0 * 5.0, VANILLA_BLOCK));
        assertFalse(FarUse.beyond(0, VANILLA_BLOCK));
    }

    @Test
    void whereATargetLies() {
        double reach = VANILLA_BLOCK + FarUse.amount(7.0, VANILLA_BLOCK);
        assertEquals(Far.NEAR, FarUse.far(4.0 * 4.0, VANILLA_BLOCK, reach));
        assertEquals(Far.FAR, FarUse.far(5.0 * 5.0, VANILLA_BLOCK, reach));
        assertEquals(Far.FAR, FarUse.far(7.9 * 7.9, VANILLA_BLOCK, reach), "vanilla's 1 block of slack past the lifted range");
        assertEquals(Far.OUT, FarUse.far(8.1 * 8.1, VANILLA_BLOCK, reach));
        double mobs = VANILLA_ENTITY + FarUse.amount(5.0, VANILLA_ENTITY);
        assertEquals(Far.FAR, FarUse.far(4.6 * 4.6, VANILLA_ENTITY, mobs), "a mob within Copper Reach past arm's length is a Far Use");
        assertEquals(Far.OUT, FarUse.far(6.1 * 6.1, VANILLA_ENTITY, mobs));
        assertEquals(Far.NEAR, FarUse.far(5.0 * 5.0, VANILLA_BLOCK, VANILLA_BLOCK), "no lift: vanilla rules alone");
        double extendo = VANILLA_BLOCK + 2.0;
        assertEquals(Far.NEAR, FarUse.far(5.0 * 5.0, extendo, extendo), "an Extendo Grip's range is the wearer's own");
    }

    @Test
    void oneChargePerTickOnePacketPerTwoTicks() {
        assertTrue(FarUse.due(Integer.MIN_VALUE / 2, 0, FarUse.CHARGE_GAP));
        assertFalse(FarUse.due(100, 100, FarUse.CHARGE_GAP), "both hands, or interact-at then interact, in one tick pay once");
        assertTrue(FarUse.due(100, 101, FarUse.CHARGE_GAP));
        assertFalse(FarUse.due(100, 101, FarUse.PACKET_GAP));
        assertTrue(FarUse.due(100, 102, FarUse.PACKET_GAP));
        assertFalse(FarUse.due(100, 119, FarUse.REFUSAL_GAP));
    }

    @Test
    void airPerFarUse() {
        assertEquals(0.5, RigAir.farUseCost(TentaworksItems.COPPER_TENTACLE_RIG.asStack()), 1.0E-9);
        assertEquals(0.25, RigAir.farUseCost(TentaworksItems.NETHERITE_TENTACLE_RIG.asStack()), 1.0E-9);
    }

    @Test
    void refusalKeysKeepTheirStrings() {
        assertEquals("tentaworks.refusal.far_use.no_sight", FarUse.NO_SIGHT);
        assertEquals("tentaworks.refusal.far_use.players", FarUse.PLAYERS);
        assertEquals("tentaworks.refusal.grab.claimed", TentaworksLang.refusalKey(TentaworksLang.GRAB, "claimed"));
    }
}
