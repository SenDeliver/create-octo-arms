package dev.tentaworks.rig;

import dev.tentaworks.registry.TentaworksDataComponents;
import dev.tentaworks.registry.TentaworksItems;
import dev.tentaworks.registry.TentaworksLang;
import dev.tentaworks.rig.FarUse.Far;
import dev.tentaworks.rig.RigSettings.FarUseMode;
import dev.tentaworks.rig.GripFaceTest.World;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Far Use: when the Actuators lend their Reach (fitted, Rig Setting, Extendo Grip, heavy hold, limp), how far it lifts the
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
        double reach = 5.0;
        assertEquals(Far.NEAR, FarUse.far(4.0 * 4.0, VANILLA_BLOCK, true, 9.0 * 9.0, reach), "within the hand's own range");
        assertEquals(Far.FAR, FarUse.far(5.0 * 5.0, VANILLA_BLOCK, true, 4.5 * 4.5, reach));
        assertEquals(Far.FAR, FarUse.far(6.3 * 6.3, VANILLA_BLOCK, true, 5.9 * 5.9, reach), "1 block of slack for the server's older view");
        assertEquals(Far.OUT, FarUse.far(6.3 * 6.3, VANILLA_BLOCK, true, 6.1 * 6.1, reach), "past Reach of the body: refused");
        assertEquals(Far.FAR, FarUse.far(5.4 * 5.4, VANILLA_BLOCK, true, 7.0 * 7.0, reach), "the hand's range + 1 still passes, as vanilla");
        assertEquals(Far.FAR, FarUse.far(4.6 * 4.6, VANILLA_ENTITY, true, 4.6 * 4.6, reach), "a mob within Reach past arm's length is a Far Use");
        assertEquals(Far.OUT, FarUse.far(6.1 * 6.1, VANILLA_ENTITY, true, 6.1 * 6.1, reach));
        assertEquals(Far.NEAR, FarUse.far(5.0 * 5.0, VANILLA_BLOCK, false, 5.0 * 5.0, reach), "no lift: vanilla rules alone");
        double extendo = VANILLA_BLOCK + 2.0;
        assertEquals(Far.NEAR, FarUse.far(5.0 * 5.0, extendo, false, 5.0 * 5.0, reach), "an Extendo Grip's range is the wearer's own");
    }

    @Test
    void theLiftCoversEveryPointWithinReachOfTheBody() {
        double reach = 5.0;
        double range = VANILLA_BLOCK + FarUse.amount(ActuatorReach.pickRange(reach), VANILLA_BLOCK);
        Vec3 body = new Vec3(0.5, 3.4, 0.5);
        Vec3 eye = body.add(0, ActuatorReach.EYE_OFFSET, 0);
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI / 32, b = (i % 8) * Math.PI / 8 - Math.PI / 2;
            Vec3 p = body.add(new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b)).scale(reach));
            assertTrue(eye.distanceTo(p) <= range + 1.0E-9, "i=" + i);
            assertTrue(ActuatorReach.accepts(eye, body, p, VANILLA_BLOCK, reach + 1.0E-9), "i=" + i);
        }
        assertFalse(ActuatorReach.accepts(eye, body, body.add(0, -5.5, 0), VANILLA_BLOCK, reach), "below the body past Reach, yet within the lifted range of the eye");
    }

    /**
     * Reach parity (CONTEXT: Reach): from Standing Height, the farthest floor block a Tentacle can still Grip (its top face within
     * Reach of the body) is one the Actuators Far-Use, and a mob standing on it is one they Grab.
     */
    @Test
    void theFloorAtTheEdgeOfTentacleReachIsFarUsableAndItsMobGrabbable() {
        for (double reach : new double[]{5.0, 6.0, 7.0}) {
            World w = new World();
            for (int x = -2; x <= 12; x++)
                for (int z = -2; z <= 2; z++) w.put(x, 0, z, Blocks.STONE.defaultBlockState());
            Vec3 feet = new Vec3(0.5, 1.0 + RigSettings.DEFAULT_HEIGHT, 0.5);
            Vec3 body = feet.add(0, 0.9, 0);
            Vec3 eye = feet.add(0, 1.62, 0);
            Grips.Face edge = null;
            for (Grips.Face f : Grips.scan(w, body, reach + 1)) {
                if (f.dir() != Direction.UP || f.block().getZ() != 0 || f.rect().distanceToSqr(body) > reach * reach) continue;
                if (edge == null || f.block().getX() > edge.block().getX()) edge = f;
            }
            assertTrue(edge != null && edge.block().getX() >= 3, "reach=" + reach);
            BlockPos pos = edge.block();
            AABB block = new AABB(pos);
            double range = VANILLA_BLOCK + FarUse.amount(ActuatorReach.pickRange(reach), VANILLA_BLOCK);
            Vec3 aim = new Vec3(pos.getX(), 1.0, 0.5);
            assertTrue(eye.distanceTo(aim) <= range, "the lifted pick from the eye gets there, reach=" + reach);
            assertTrue(ActuatorReach.accepts(eye, body, aim, VANILLA_BLOCK, reach), "the client keeps the hit, reach=" + reach);
            assertEquals(Far.FAR, FarUse.far(block.distanceToSqr(eye), VANILLA_BLOCK, true, block.distanceToSqr(body), reach), "reach=" + reach);
            assertTrue(WorkSight.seesBlock(w, pos, Direction.UP, aim, eye, aim.subtract(eye).normalize(), range + 1.0, CollisionContext.empty()), "reach=" + reach);

            AABB mob = new AABB(pos.getX() + 0.2, 1.0, 0.2, pos.getX() + 0.8, 2.95, 0.8);
            GrabRule.Target t = new GrabRule.Target(true, false, false, false, false, false, 20, 0.6, 1.95, false, Math.sqrt(mob.distanceToSqr(body)),
                    SetDown.sight(w, body, mob, CollisionContext.empty()));
            assertEquals(GrabRule.Verdict.LIGHT, GrabRule.check(t, new GrabRule.Rules(reach, false)), "reach=" + reach);
        }
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
    void theFloorAheadAtCopperReachIsAFarUseInSight() {
        double reach = VANILLA_BLOCK + FarUse.amount(5.0, VANILLA_BLOCK);
        World w = new World();
        for (int x = -2; x <= 8; x++)
            for (int z = -2; z <= 2; z++) w.put(x, 0, z, Blocks.STONE.defaultBlockState());
        Vec3 eye = new Vec3(0.5, 2.62, 0.5);
        for (int x = 5; x <= 6; x++) {
            BlockPos pos = new BlockPos(x, 0, 0);
            assertEquals(Far.FAR, FarUse.far(new AABB(pos).distanceToSqr(eye), VANILLA_BLOCK, true, new AABB(pos).distanceToSqr(eye.subtract(0, 0.72, 0)), 5.0), "x=" + x);
            for (double dx = -0.75; dx <= 0.75; dx += 0.25) {
                Vec3 look = new Vec3(x + 0.5 + dx, 1.0, 0.5).subtract(eye).normalize();
                assertTrue(WorkSight.seesBlock(w, pos, Direction.UP, null, eye, look, reach + 1.0, CollisionContext.empty()), "x=" + x + " dx=" + dx);
                assertTrue(WorkSight.seesBlock(w, pos, null, null, eye, look, reach + 1.0, CollisionContext.empty()), "x=" + x + " dx=" + dx);
            }
        }
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
