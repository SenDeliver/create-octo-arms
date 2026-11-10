package dev.tentaworks.rig;

import dev.tentaworks.rig.GrabRule.Rules;
import dev.tentaworks.rig.GrabRule.Target;
import dev.tentaworks.rig.GrabRule.Verdict;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who an Actuator may Grab, every refusal reason, and which targets are light (one Actuator) or heavy (both). */
class GrabRuleTest {
    private static final Rules RULES = new Rules(4.0, false);

    private static Target mob(double width, double height, double maxHealth, double distance) {
        return new Target(true, false, false, false, false, false, maxHealth, width, height, false, distance, true);
    }

    private static Verdict check(Target t) {
        return GrabRule.check(t, RULES);
    }

    @Test
    void everyRefusal() {
        Target pig = mob(0.9, 0.9, 10, 2);
        assertEquals(Verdict.LIGHT, check(pig));
        assertEquals(Verdict.GONE, check(new Target(false, true, false, false, false, false, 0, 0, 0, false, Double.MAX_VALUE, false)));
        assertEquals(Verdict.PLAYER, check(new Target(true, false, true, false, false, false, 20, 0.6, 1.8, false, 2, true)));
        assertEquals(Verdict.NOT_LIVING, check(new Target(false, false, false, false, false, false, 0, 0.5, 0.5, false, 2, true)));
        assertEquals(Verdict.BOSS, check(new Target(true, false, false, false, false, true, 20, 0.9, 0.9, false, 2, true)));
        assertEquals(Verdict.RIDING, check(new Target(true, false, false, true, false, false, 10, 0.9, 0.9, false, 2, true)));
        assertEquals(Verdict.HELD, check(new Target(true, false, false, false, true, false, 10, 0.9, 0.9, false, 2, true)));
        assertEquals(Verdict.OWNED, check(new Target(true, false, false, false, false, false, 20, 0.6, 0.85, true, 2, true)));
        assertEquals(Verdict.TOO_FAR, check(mob(0.9, 0.9, 10, 9)));
        assertEquals(Verdict.NO_SIGHT, check(new Target(true, false, false, false, false, false, 10, 0.9, 0.9, false, 2, false)));
        for (Verdict v : Verdict.values()) {
            assertEquals(v.ok(), v == Verdict.LIGHT || v == Verdict.HEAVY, v.name());
            assertEquals(v.ok() ? v == Verdict.HEAVY ? 2 : 1 : 0, v.actuators(), v.name());
        }
    }

    @Test
    void aShipThatIsAllThatHoldsTheWearerUpIsNotGrabbed() {
        String ship = "deck", other = "raft";
        assertTrue(GrabRule.holdsUp(ship, true, false, List.of(ship, ship, ship, ship)), "every Grip on it");
        assertTrue(GrabRule.holdsUp(ship, true, false, List.of(ship, ship)), "two Grips, both on it");
        assertFalse(GrabRule.holdsUp(ship, true, false, Arrays.asList(ship, ship, ship, null)), "one Grip on a world block is enough");
        assertFalse(GrabRule.holdsUp(ship, true, false, List.of(ship, ship, other)), "one Grip on another Ship is enough");
        assertFalse(GrabRule.holdsUp(ship, true, true, Arrays.asList(ship, null)), "the Rig carries: feet on its deck do not count");
        assertFalse(GrabRule.holdsUp(ship, true, false, Arrays.asList(null, null)), "no Grip on it");
        assertTrue(GrabRule.holdsUp(ship, false, true, List.of()), "Stowed, feet on it");
        assertFalse(GrabRule.holdsUp(ship, false, false, List.of()), "Stowed on the ground");
        assertTrue(GrabRule.holdsUp(ship, false, true, Arrays.asList(null, null)), "Falling: Grips no longer hold, the feet decide");
        assertTrue(GrabRule.holdsUp(ship, true, true, List.of()), "a carrying Rig with no Grip: the feet decide");
        assertFalse(GrabRule.holdsUp(ship, true, false, List.of()));
        assertEquals("OWN", Verdict.OWN.name(), "its lower-case name is the refusal key own");
    }

    @Test
    void bossByTagOrHealth() {
        assertEquals(Verdict.BOSS, check(mob(0.9, 3.5, GrabRule.BOSS_HEALTH + 1, 2)));
        assertEquals(Verdict.HEAVY, check(mob(1.4, 2.7, GrabRule.BOSS_HEALTH, 2)), "an Iron Golem, exactly 100 max health");
        assertEquals(Verdict.BOSS, check(new Target(true, false, false, false, false, true, 1, 0.9, 0.9, false, 2, true)), "tagged, whatever its health");
    }

    @Test
    void bossesTagNamesTheVanillaBosses() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("data/tentaworks/tags/entity_type/bosses.json")) {
            assertNotNull(in);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String id : new String[]{"#c:bosses", "minecraft:ender_dragon", "minecraft:wither", "minecraft:warden"}) assertTrue(json.contains(id), id);
        }
    }

    /** Mob and Ship Grabs both ask the protection check; a no from mayInteract never posts the event. */
    @Test
    void claimGate() {
        int[] posted = {0};
        assertFalse(GrabRule.claimed(true, () -> posted[0]++ < 0));
        assertEquals(1, posted[0]);
        assertTrue(GrabRule.claimed(true, () -> ++posted[0] > 0), "a claim mod cancelled the event");
        assertTrue(GrabRule.claimed(false, () -> ++posted[0] > 0), "spawn protection");
        assertEquals(2, posted[0]);
    }

    @Test
    void othersAnimalsOnlyWithConfig() {
        Target dog = new Target(true, false, false, false, false, false, 20, 0.6, 0.85, true, 2, true);
        assertEquals(Verdict.OWNED, GrabRule.check(dog, RULES));
        assertEquals(Verdict.LIGHT, GrabRule.check(dog, new Rules(4, true)));
    }

    @Test
    void reachIsFromTheBodyToTheNearestPointPlusSlack() {
        double edge = 4.0 + GrabRule.SLACK;
        assertEquals(Verdict.LIGHT, check(mob(0.9, 0.9, 10, edge - 0.01)));
        assertEquals(Verdict.TOO_FAR, check(mob(0.9, 0.9, 10, edge + 0.01)));
        assertEquals(Verdict.LIGHT, GrabRule.check(mob(0.9, 0.9, 10, edge + 1.4), new Rules(5.5, false)));
    }

    @Test
    void aLightTargetTakesTheOffHandActuatorFirst() {
        assertEquals(HoldArm.OFF, GrabRule.arm(1, true, true));
        assertEquals(HoldArm.OFF, GrabRule.arm(1, false, true), "an item in the main hand: the off-hand Actuator Grabs");
        assertEquals(HoldArm.MAIN, GrabRule.arm(1, true, false), "the off hand holds something: the main-hand Actuator");
        assertEquals(HoldArm.NONE, GrabRule.arm(1, false, false));
        assertEquals(Verdict.HANDS_FULL, GrabRule.hands(1));
    }

    @Test
    void aHeavyTargetOrAShipTakesBothHandsEmpty() {
        assertEquals(HoldArm.BOTH, GrabRule.arm(2, true, true));
        assertEquals(HoldArm.NONE, GrabRule.arm(2, false, true));
        assertEquals(HoldArm.NONE, GrabRule.arm(2, true, false));
        assertEquals(HoldArm.NONE, GrabRule.arm(2, false, false));
        assertEquals(Verdict.TWO_HANDS, GrabRule.hands(2));
        assertEquals("two_hands", Verdict.TWO_HANDS.name().toLowerCase(java.util.Locale.ROOT));
        assertEquals("hands_full", Verdict.HANDS_FULL.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Test
    void holdArmSides() {
        assertEquals(net.minecraft.world.entity.HumanoidArm.RIGHT, HoldArm.MAIN.side(net.minecraft.world.entity.HumanoidArm.RIGHT));
        assertEquals(net.minecraft.world.entity.HumanoidArm.LEFT, HoldArm.OFF.side(net.minecraft.world.entity.HumanoidArm.RIGHT));
        assertEquals(net.minecraft.world.entity.HumanoidArm.RIGHT, HoldArm.OFF.side(net.minecraft.world.entity.HumanoidArm.LEFT), "left-handed: the off hand is the right");
        assertEquals(HoldArm.OFF, HoldArm.of(net.minecraft.world.entity.HumanoidArm.LEFT, net.minecraft.world.entity.HumanoidArm.RIGHT));
        assertEquals(HoldArm.MAIN, HoldArm.of(net.minecraft.world.entity.HumanoidArm.LEFT, net.minecraft.world.entity.HumanoidArm.LEFT));
        for (HoldArm a : HoldArm.values()) assertEquals(a, HoldArm.byId(a.ordinal()));
        assertEquals(HoldArm.NONE, HoldArm.byId(99));
        assertTrue(HoldArm.BOTH.uses(net.minecraft.world.InteractionHand.MAIN_HAND) && HoldArm.BOTH.uses(net.minecraft.world.InteractionHand.OFF_HAND));
        assertFalse(HoldArm.OFF.uses(net.minecraft.world.InteractionHand.MAIN_HAND));
        assertFalse(HoldArm.NONE.holding());
    }

    @Test
    void lightOrHeavyTable() {
        Object[][] table = {
                {"zombie", 0.6, 1.95, true}, {"villager", 0.6, 1.95, true}, {"cow", 0.9, 1.4, true}, {"spider", 1.4, 0.9, true},
                {"pig", 0.9, 0.9, true}, {"horse", 1.3964844, 1.6, false}, {"enderman", 0.6, 2.9, false}, {"iron golem", 1.4, 2.7, false},
                {"ravager", 1.95, 2.2, false}, {"ghast", 4.0, 4.0, false}, {"widest light", 1.5, 0.88, true}, {"too bulky", 1.5, 0.9, false},
                {"tallest light", 0.6, 2.0, true}, {"too tall", 0.6, 2.01, false}, {"too wide", 1.51, 0.5, false}};
        for (Object[] row : table)
            assertEquals(row[3], GrabRule.light((double) row[1], (double) row[2]), (String) row[0]);
    }

    /** Against the real vanilla hitboxes: the horse is heavy by bulk, not by its width or height alone. */
    @Test
    void vanillaHitboxes() {
        assertTrue(light(EntityType.ZOMBIE));
        assertTrue(light(EntityType.COW));
        assertTrue(light(EntityType.SPIDER));
        assertTrue(light(EntityType.VILLAGER));
        assertFalse(light(EntityType.HORSE));
        assertTrue(EntityType.HORSE.getWidth() <= GrabRule.LIGHT_WIDTH && EntityType.HORSE.getHeight() <= GrabRule.LIGHT_HEIGHT);
        assertFalse(light(EntityType.ENDERMAN));
        assertFalse(light(EntityType.IRON_GOLEM));
        assertFalse(light(EntityType.RAVAGER));
        assertFalse(light(EntityType.GHAST));
    }

    private static boolean light(EntityType<?> type) {
        return GrabRule.light(type.getWidth(), type.getHeight());
    }

    @Test
    void anAttackWhileHoldingThrowsWithAnEmptyMainHandOrTheGrabKeyHeld() {
        assertTrue(GrabRule.throwsOnAttack(true, false));
        assertTrue(GrabRule.throwsOnAttack(false, true), "the Grab key held: Throw even with an item");
        assertTrue(GrabRule.throwsOnAttack(true, true));
        assertFalse(GrabRule.throwsOnAttack(false, false), "an item in the main hand: the plain attack is the item's");
    }
}
