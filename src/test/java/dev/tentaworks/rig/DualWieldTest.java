package dev.tentaworks.rig;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dual Wield: when it works, the mining speed sum, the melee bonus. */
class DualWieldTest {
    @Test
    void needsTheConfigAFittedReadyRigAFreeOffHandActuatorAndAnOffHandItem() {
        assertTrue(DualWield.allows(true, true, true, HoldArm.NONE, false));
        assertTrue(DualWield.allows(true, true, true, HoldArm.MAIN, false), "the main-hand Actuator may hold a light mob");
        assertFalse(DualWield.allows(true, true, true, HoldArm.OFF, false), "the off-hand Actuator holds");
        assertFalse(DualWield.allows(true, true, true, HoldArm.BOTH, false), "a heavy hold or a Ship");
        assertFalse(DualWield.allows(false, true, true, HoldArm.NONE, false), "actuators.dualWield off");
        assertFalse(DualWield.allows(true, false, true, HoldArm.NONE, false), "no Actuators");
        assertFalse(DualWield.allows(true, true, false, HoldArm.NONE, false), "limp, no Air");
        assertFalse(DualWield.allows(true, true, true, HoldArm.NONE, true), "nothing in the off hand");
    }

    @Test
    void miningSpeedIsTheSumOfBothEffectiveTools() {
        assertEquals((8 + 26 + 6) / (8.0 + 26), DualWield.speedFactor(8, 26, 6, 0), 1.0E-9, "diamond pickaxe Efficiency V + stone pickaxe");
        assertEquals(2.0, DualWield.speedFactor(8, 0, 8, 0), 1.0E-9, "two of the same: twice as fast");
        assertEquals((8 + 8 + 26) / 8.0, DualWield.speedFactor(8, 0, 8, 26), 1.0E-9, "the off tool's own Efficiency counts");
        assertEquals(6.0, DualWield.speedFactor(1, 26, 6, 0), 1.0E-9, "a bare main hand: the off tool alone, the main hand's Efficiency not added");
        assertEquals(1.0, DualWield.speedFactor(8, 0, 1, 26), 1.0E-9, "an off-hand item that is no tool here adds nothing");
        assertEquals(1.0, DualWield.speedFactor(0, 0, 8, 0), 1.0E-9);
    }

    @Test
    void realItems() {
        assertEquals(0, DualWield.efficiency(new ItemStack(Items.IRON_PICKAXE)), 1.0E-9);
        assertEquals(5.0, DualWield.attackBonus(new ItemStack(Items.IRON_SWORD)), 1.0E-9, "iron sword: 6 = 1 + 5");
        assertEquals(0, DualWield.attackBonus(new ItemStack(Items.STICK)), 1.0E-9);
        assertFalse(DualWield.effective(new ItemStack(Items.STICK).getDestroySpeed(Blocks.STONE.defaultBlockState())));
    }

    @Test
    void meleeBonusScalesWithAttackStrengthAsTheMainHandDoes() {
        assertEquals(5.0F, DualWield.bonus(5, 0, 1.0F), 1.0E-6, "a full swing: the off sword's whole bonus");
        assertEquals(5 * (0.2F + 0.25F * 0.8F), DualWield.bonus(5, 0, 0.5F), 1.0E-6);
        assertEquals(5 * (0.2F + 0.8F * 0.25F) + 2.5F * 0.5F, DualWield.bonus(5, 2.5, 0.5F), 1.0E-6, "Sharpness scales linearly");
        assertEquals(1.0F, DualWield.bonus(5, 0, 0.0F), 1.0E-6, "spam-clicking keeps a fifth");
        assertEquals(0.0F, DualWield.bonus(0, 0, 1.0F), 1.0E-6);
    }

    @Test
    void airPerBonusAction() {
        assertEquals(0.5, RigAir.actuatorCost(dev.tentaworks.registry.TentaworksItems.COPPER_TENTACLE_RIG.asStack(), 0.5), 1.0E-9);
        assertEquals(0.25, RigAir.actuatorCost(dev.tentaworks.registry.TentaworksItems.NETHERITE_TENTACLE_RIG.asStack(), 0.5), 1.0E-9);
    }

    @Test
    void theMeleeBonusHitsPlayersOnlyWhenFarUsePlayersAllows() {
        assertTrue(DualWield.targets(true, false, false), "a mob");
        assertFalse(DualWield.targets(false, false, true), "not living: an Armor Stand is living, an item frame is not");
        assertFalse(DualWield.targets(true, true, false), "a player, farUse.players off");
        assertTrue(DualWield.targets(true, true, true), "a player, farUse.players on");
    }
}
