package dev.tentaworks.rig;

import dev.tentaworks.compat.Pose;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Turn by hand: the stroke limit, the regrip choice and its order, the pivot change that keeps the Ship where it is, a 180° Turn. */
class ShipHandsTest {
    private static final Vec3 FEET = new Vec3(0, 64, 0), EYE = FEET.add(0, 1.62, 0);
    private static final float YAW = -90;
    /** A 3 × 3 × 3 hull in plot space, its body pivot in the middle. */
    private static final AABB CUBE = new AABB(0, 0, 0, 3, 3, 3);
    private static final Vector3d PIVOT = new Vector3d(1.5, 1.5, 1.5);

    private static final ShipHands.Ray RAY = (from, to) -> {
        Optional<Vec3> at = CUBE.clip(from, to);
        if (at.isEmpty()) return null;
        Vec3 p = at.get();
        Direction n = Direction.UP;
        double best = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            double plane = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? CUBE.max(d.getAxis()) : CUBE.min(d.getAxis());
            double gap = Math.abs(d.getAxis().choose(p.x, p.y, p.z) - plane);
            if (gap < best) {
                best = gap;
                n = d;
            }
        }
        return new ShipHands.Hit(p, Vec3.atLowerCornerOf(n.getNormal()));
    };

    private static Vec3[] shoulders() {
        return new Vec3[] {Hold.shoulder(FEET, YAW, 1.8, -1), Hold.shoulder(FEET, YAW, 1.8, 1)};
    }

    private static Pose pose(Vec3 position, Quaterniond q) {
        return new Pose(new Vector3d(position.x, position.y, position.z), q, PIVOT);
    }

    @Test
    void theStrokeIsSpentPastFortyFiveDegreesOrASurfaceTurnedAwayOrAStretch() {
        Quaterniond q = new Quaterniond().rotateY(Math.toRadians(50));
        assertEquals(50, ShipHands.strokeAngle(new Quaterniond(), q), 1.0E-6);
        assertEquals(20, ShipHands.strokeAngle(new Quaterniond().rotateY(Math.toRadians(30)), q), 1.0E-6, "since it took hold");
        Vec3 s = new Vec3(0, 0, 0), at = new Vec3(2, 0, 0);
        assertTrue(ShipHands.over(10, new Vec3(-1, 0, 0), at, s, 2) < 1, "facing the shoulder, early in the stroke");
        assertTrue(ShipHands.over(46, new Vec3(-1, 0, 0), at, s, 2) > 1, "turned past the stroke");
        Vec3 away = new Vec3(-Math.cos(Math.toRadians(65)), 0, Math.sin(Math.toRadians(65)));
        assertTrue(ShipHands.over(0, away, at, s, 2) > 1, "its surface 65 degrees away from the shoulder");
        assertTrue(ShipHands.over(0, new Vec3(-1, 0, 0), new Vec3(3.2, 0, 0), s, 2) > 1, "1.2 farther than it took hold");
        assertEquals(2, ShipHands.over(0, new Vec3(-1, 0, 0), new Vec3(0.2, 0, 0), s, 2), "into the shoulder");
    }

    @Test
    void aNewPointMustBeAwayFromTheOldAndFaceTheShoulderWellInsideTheLimit() {
        Pose p = pose(new Vec3(4, 65.62, 0), new Quaterniond());
        Vec3 s = new Vec3(0, 65.5, 0);
        ShipHands.Hit west = new ShipHands.Hit(new Vec3(0, 1.5, 1.5), new Vec3(-1, 0, 0));
        assertTrue(ShipHands.worth(west, p, new Vec3(0, 1.5, 0.3), s, 7));
        assertFalse(ShipHands.worth(west, p, new Vec3(0, 1.5, 1.4), s, 7), "nearly where it holds");
        assertFalse(ShipHands.worth(west, p, new Vec3(0, 1.5, 0.3), s, 2), "beyond Reach");
        ShipHands.Hit side = new ShipHands.Hit(new Vec3(1.5, 1.5, 0), new Vec3(0, 0, -1));
        assertFalse(ShipHands.worth(side, p, new Vec3(0, 1.5, 1.5), s, 7), "a face seen edge on");
    }

    @Test
    void theFartherPastGoesFirstAndNeverBothLetGo() {
        Quaterniond start = new Quaterniond();
        Pose p = pose(new Vec3(4, 65.62, 0), new Quaterniond().rotateY(Math.toRadians(60)));
        ShipHands hands = new ShipHands(new Vec3(0, 1.5, 1.1), new Vec3(0, 1.5, 1.9), new Vec3(-1, 0, 0), start);
        Quaterniond user = new Quaterniond().rotateY(Math.toRadians(60));
        Vec3 centre = p.toWorld(CUBE.getCenter()), right = Hold.yawBasis(YAW)[1];
        List<ShipHands.Change> seen = new ArrayList<>();
        int first = -1;
        for (int tick = 0; tick < 40; tick++) {
            ShipHands.Change c = hands.tick(p, user, shoulders(), centre, right, RAY, 7);
            seen.add(c);
            assertTrue(hands.tip(0).holding() || hands.tip(1).holding(), "never both off, tick " + tick);
            if (c == ShipHands.Change.LET_GO && first < 0) first = hands.tip(0).holding() ? 1 : 0;
        }
        assertTrue(first >= 0, "a regrip " + seen);
        assertEquals(1, hands.tip(0).regrips(), "the left one once");
        assertEquals(1, hands.tip(1).regrips(), "the right one once");
        assertTrue(hands.tip(0).holding() && hands.tip(1).holding());
        int held = seen.indexOf(ShipHands.Change.TOOK_HOLD);
        assertEquals(ShipHands.Change.LET_GO, seen.get(held + 1), "the other goes the tick after the first holds " + seen);
    }

    @Test
    void noBetterPointKeepsTheOldOneAndRests() {
        Pose p = pose(new Vec3(4, 65.62, 0), new Quaterniond());
        ShipHands hands = new ShipHands(new Vec3(0, 1.5, 1.1), new Vec3(0, 1.5, 1.9), new Vec3(-1, 0, 0), new Quaterniond());
        Quaterniond user = new Quaterniond().rotateY(Math.toRadians(50));
        for (int tick = 0; tick < 30; tick++)
            assertEquals(ShipHands.Change.NONE, hands.tick(p, user, shoulders(), new Vec3(4, 65.62, 0), Hold.yawBasis(YAW)[1], (a, b) -> null, 7));
        assertEquals(new Vec3(0, 1.5, 1.1), hands.tip(0).at(), "nothing hit: the old point");
        assertTrue(hands.tip(0).holding() && hands.tip(1).holding());
    }

    @Test
    void aNewPivotKeepsTheShipWhereItIsThenSettlesBackToTheHold() {
        Quaterniond want = new Quaterniond().rotateY(0.9).rotateX(0.2);
        Vec3 lastAim = new Vec3(4.2, 65.9, 0.7), local = new Vec3(0.2, 1.1, 2.6);
        Vec3 was = Hold.shipWorld(lastAim, want, local, PIVOT);
        for (boolean overhead : new boolean[] {false, true}) {
            Vec3[] b = overhead ? Hold.yawBasis(YAW) : Hold.basis(Vec3.directionFromRotation(20, YAW), YAW);
            Vec3 base = overhead ? new Vec3(0.5, 68.4, 0) : Hold.shipPoint(EYE, Vec3.directionFromRotation(20, YAW), YAW, new Vec3(2.5, 0, 0));
            Vec3 c = Hold.correction(b, base, was);
            Vec3 aim = Hold.shipAim(Hold.corrected(b, base, c), want, local, PIVOT);
            assertEquals(0, aim.distanceTo(lastAim), 1.0E-9, "the same body aim on the switch tick, overhead " + overhead);
            double before = c.length();
            for (int i = 0; i < 40; i++) {
                Vec3 next = Hold.settle(c);
                assertTrue(c.length() - next.length() <= Hold.SETTLE_STEP + 1.0E-9, "at most a quarter block a tick");
                c = next;
            }
            assertTrue(before > 0.5, "a real correction " + before);
            assertEquals(Vec3.ZERO, c, "settled to the hold");
        }
    }

    @Test
    void theTurnIsBankedAndTurnedNineDegreesATickHalfWhileATipReaches() {
        double[] bank = {0, 0};
        for (int i = 0; i < 4; i++) bank = Hold.turnBank(bank, 30, -10);
        assertEquals(90, bank[0], 1.0E-9, "kept within 90");
        assertEquals(-40, bank[1], 1.0E-9);
        assertEquals(9, Hold.turnStep(bank, false)[0], 1.0E-9);
        assertEquals(-4.5, Hold.turnStep(bank, true)[1], 1.0E-9);
        assertEquals(1.35, Hold.turnStep(bank, false, 1.35)[0], 1.0E-9, "a Ship at the mass cap");
        assertEquals(-0.675, Hold.turnStep(bank, true, 1.35)[1], 1.0E-9);
        assertEquals(30, Hold.turnBank(new double[] {0, 0}, 500, 0)[0], 1.0E-9, "one input clamped");
    }

    /**
     * The harness V Turn in plain math: a 3 × 3 × 3 hull held by its west face 2.5 ahead, the body tracking the aim exactly, Turned
     * 180 degrees right in 12 moves of 15. The Turn total is what was asked, both tips regripped and end on the wearer's side of
     * the hull, never both off, the hull never jumps on a regrip and stays in front of the wearer, swung a little to the side as a box
     * turned by its near side swings.
     */
    @Test
    void aHalfTurnByHandEndsWithBothTipsOnTheWearersSide() {
        Quaterniond start = new Quaterniond();
        Vec3 look = Vec3.directionFromRotation(0, YAW);
        Vec3 face = new Vec3(0, 1.5, 1.5);
        Pose p = pose(new Vec3(4, 65.62, 0), start);
        Vec3 rel = Hold.shipRel(EYE, look, YAW, p.toWorld(face), 6.5);
        Vec3 t = Vec3.atLowerCornerOf(Hold.faceTangent(Direction.WEST, p.normalToLocal(Hold.basis(look, YAW)[1])).getNormal()).scale(Hold.GRIP_SPREAD);
        ShipHands hands = new ShipHands(face.subtract(t), face.add(t), new Vec3(-1, 0, 0), start);
        Quaterniond user = new Quaterniond();
        double[] bank = {0, 0};
        Vec3 local = hands.pivot(), c = Vec3.ZERO, lastAim = null;
        Quaterniond lastWant = null;
        Vec3[] b = Hold.basis(look, YAW);
        double turnStepMax = 0, switchStepMax = 0;
        int switches = 0;
        double startGap = p.toWorld(CUBE.getCenter()).distanceTo(EYE);
        for (int tick = 0; tick < 12 * 14 + 60; tick++) {
            if (tick < 12 * 14 && tick % 14 == 0) bank = Hold.turnBank(bank, 15, 0);
            double[] step = Hold.turnStep(bank, hands.reaching());
            user = Hold.shipTurnInput(user, YAW, step[0], step[1]).normalize();
            bank = new double[] {bank[0] - step[0], bank[1] - step[1]};
            Quaterniond want = Hold.shipWant(start, 0, user);
            ShipHands.Change ch = hands.tick(p, user, shoulders(), p.toWorld(CUBE.getCenter()), b[1], RAY, 7);
            assertTrue(hands.tip(0).holding() || hands.tip(1).holding(), "never both off, tick " + tick);
            if (ch == ShipHands.Change.TOOK_HOLD) {
                Vec3 was = Hold.shipWorld(lastAim, lastWant, hands.pivot(), PIVOT);
                local = hands.pivot();
                c = Hold.correction(b, Hold.shipPoint(EYE, look, YAW, rel), was);
                switches++;
            }
            Vec3 aim = Hold.shipAim(Hold.corrected(b, Hold.shipPoint(EYE, look, YAW, rel), c), want, local, PIVOT);
            c = Hold.settle(c);
            if (lastAim != null) {
                double d = aim.distanceTo(lastAim);
                if (ch == ShipHands.Change.TOOK_HOLD) switchStepMax = Math.max(switchStepMax, d);
                else if (step[0] != 0) turnStepMax = Math.max(turnStepMax, d);
            }
            lastAim = aim;
            lastWant = want;
            p = pose(aim, want);
        }
        assertEquals(180, ShipHands.strokeAngle(new Quaterniond(), user), 0.01, "the Turn total as asked");
        assertEquals(0, bank[0], 1.0E-9, "the bank drained");
        assertTrue(hands.tip(0).regrips() >= 1 && hands.tip(1).regrips() >= 1, "both regripped: " + hands.tip(0).regrips() + " " + hands.tip(1).regrips());
        assertTrue(switches >= 2, "switches " + switches);
        Vec3 centre = p.toWorld(CUBE.getCenter());
        for (int i = 0; i < 2; i++) {
            Vec3 w = p.toWorld(hands.tip(i).at());
            assertTrue(w.distanceTo(EYE) < centre.distanceTo(EYE), "tip " + i + " on the wearer's side: " + w + " centre " + centre);
            assertTrue(CUBE.inflate(1.0E-6).contains(hands.tip(i).at()), "on the hull");
        }
        assertTrue(switchStepMax <= turnStepMax + 1.0E-6, "no jump on a regrip: " + switchStepMax + " vs a turn step " + turnStepMax);
        assertEquals(startGap, centre.distanceTo(EYE), 1.0, "the hull stays at its distance");
        assertTrue(Math.abs(centre.z) < CUBE.getXsize() / 2, "and in front, the crosshair still on it (it swings about the near side it is held by): " + centre);
    }
}
