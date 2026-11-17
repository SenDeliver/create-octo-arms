package dev.tentaworks.rig;

import dev.tentaworks.compat.Pose;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The held Ship's build: Ships whose boxes touch it, step by step, bounded; a separate Ship stays out. */
class ShipBuildTest {
    private record Part(String id, AABB box) {
    }

    private static List<String> build(Part seed, List<Part> all) {
        Function<AABB, List<Part>> near = q -> {
            List<Part> out = new ArrayList<>();
            for (Part p : all) if (p.box() != null && p.box().intersects(q)) out.add(p);
            return out;
        };
        return ShipBuild.group(seed, Part::box, near, Part::id).stream().map(Part::id).toList();
    }

    private static AABB box(double x, double y, double z, double w, double h, double d) {
        return new AABB(x, y, z, x + w, y + h, z + d);
    }

    @Test
    void aMechOfTouchingPartsIsOneBuild() {
        Part body = new Part("body", box(0, 3, 0, 3, 2, 3));
        Part leg = new Part("leg", box(0.5, 0, 0.5, 1, 3.2, 1));
        Part foot = new Part("foot", box(0, -1, 0, 2, 1.3, 2));
        assertEquals(List.of("body", "leg", "foot"), build(body, List.of(body, leg, foot)), "the foot joins through the leg");
        assertEquals(List.of("foot", "leg", "body"), build(foot, List.of(body, leg, foot)));
    }

    @Test
    void withinTheMarginTouchesBeyondItDoesNot() {
        Part held = new Part("held", box(0, 0, 0, 1, 1, 1));
        Part near = new Part("near", box(1.4, 0, 0, 1, 1, 1));
        Part far = new Part("far", box(1.6, 0, 0, 1, 1, 1));
        assertEquals(List.of("held", "near"), build(held, List.of(held, near)), "a 0.4 gap is touching");
        assertEquals(List.of("held"), build(held, List.of(held, far)), "a 0.6 gap is a separate Ship");
    }

    @Test
    void aSeparateDeckUnderTheHeldShipIsNotPartOfIt() {
        Part deck = new Part("deck", box(-10, 0, -10, 20, 1, 20));
        Part crate = new Part("crate", box(0, 2, 0, 1, 1, 1));
        assertEquals(List.of("crate"), build(crate, List.of(deck, crate)), "held a block above the deck");
        Part low = new Part("low", box(0, 1.3, 0, 1, 1, 1));
        assertEquals(List.of("low", "deck"), build(low, List.of(deck, low)), "lowered within 0.5 of the deck it joins");
    }

    @Test
    void unknownBoxesJoinNothingAndTheCountIsBounded() {
        Part held = new Part("held", box(0, 0, 0, 1, 1, 1));
        Part ghost = new Part("ghost", null);
        assertEquals(List.of("held"), ShipBuild.group(held, Part::box, q -> List.of(held, ghost), Part::id).stream().map(Part::id).toList());
        assertEquals(List.of("ghost"), ShipBuild.group(ghost, Part::box, q -> List.of(held), Part::id).stream().map(Part::id).toList());
        List<Part> chain = new ArrayList<>();
        for (int i = 0; i < 40; i++) chain.add(new Part("p" + i, box(i, 0, 0, 1, 1, 1)));
        assertEquals(ShipBuild.MAX, build(chain.get(0), chain).size());
    }

    @Test
    void theWorldBoxFollowsThePose() {
        AABB local = box(100, 0, 100, 3, 1, 1);
        Pose moved = new Pose(new Vector3d(10, 5, 0), new Quaterniond(), new Vector3d(100, 0, 100));
        AABB w = ShipBuild.worldBox(moved, local);
        assertEquals(10, w.minX, 1e-9);
        assertEquals(13, w.maxX, 1e-9);
        assertEquals(5, w.minY, 1e-9);
        Pose turned = new Pose(new Vector3d(0, 0, 0), new Quaterniond().rotateY(Math.PI / 2), new Vector3d(100, 0, 100));
        AABB t = ShipBuild.worldBox(turned, local);
        assertEquals(1, t.getXsize(), 1e-9, "a quarter turn: 3 long along z now");
        assertEquals(3, t.getZsize(), 1e-9);
    }
}
