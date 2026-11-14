package dev.tentaworks.client;

import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static dev.tentaworks.client.ActuatorPose.*;
import static org.junit.jupiter.api.Assertions.*;

class ActuatorPoseTest {
    private static final int[] SIDES = {LEFT, RIGHT};

    private static Chain rest(int side, float t) {
        Vec3 s = shoulder(side), w = restWrist(side, t);
        return chain(s, w, restBulge(side), new Vec3(side, 0.3, 0), segment(s.distanceTo(w), restStraight(t), 4));
    }

    @Test
    void restPoseIsAboveTheShouldersAndWiderThanTheBody() {
        for (int side : SIDES) {
            for (float t = 0; t < 400; t += 3.7F) {
                Chain c = rest(side, t);
                Vec3 wrist = c.wrist();
                assertEquals(restWrist(side, t).x, wrist.x, 1.0E-6, "wrist reaches its rest point");
                assertEquals(restWrist(side, t).y, wrist.y, 1.0E-6);
                assertTrue(Math.abs(sideways(wrist)) > BODY_HALF_WIDTH, "wider than the body: " + sideways(wrist));
                assertTrue(heightAboveFeet(wrist) > HEAD_TOP, "above the head: " + heightAboveFeet(wrist));
                for (int j = 1; j < c.joints().length; j++) {
                    assertEquals(side, Math.signum(sideways(c.joints()[j])), "joint " + j + " on its own side");
                    assertTrue(c.joints()[j].y > shoulder(side).y, "joint " + j + " above the shoulder");
                }
                float open = restOpen(side, t);
                assertTrue(open >= 0 && open <= 1, "gripper opening " + open);
            }
        }
    }

    @Test
    void restPoseIsSymmetric() {
        for (float t = 0; t < 300; t += 2.9F) {
            Vec3[] l = rest(LEFT, t).joints(), r = rest(RIGHT, t).joints();
            for (int j = 0; j < l.length; j++) {
                assertEquals(1 - l[j].x, r[j].x, 1.0E-9, "mirrored x, joint " + j);
                assertEquals(l[j].y, r[j].y, 1.0E-9);
                assertEquals(l[j].z, r[j].z, 1.0E-9);
            }
        }
    }

    @Test
    void segmentsAreRigidAlongTheChain() {
        Vec3 s = new Vec3(0.2, 0.8, 0.4);
        Vec3[] targets = {new Vec3(0.3, 1.6, 0.4), new Vec3(1.5, 0.8, -2), new Vec3(0.2, 0.8, 0.4), new Vec3(0.2, 9, 0.4),
                new Vec3(0.2, -9, 0.4), new Vec3(0.2, 0.8, 6), new Vec3(-4, 3, -12), new Vec3(0.2, 0.8001, 0.4)};
        Vec3[] bulges = {new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(1, 0, 0), Vec3.ZERO};
        for (Vec3 w : targets) {
            for (Vec3 bulge : bulges) {
                for (double seg : new double[]{MIN_SEGMENT, 0.3, 1.0, 5.5}) {
                    Chain c = chain(s, w, bulge, new Vec3(-1, 0.3, 0), seg);
                    assertEquals(SEGMENTS + 1, c.joints().length);
                    assertEquals(s, c.joints()[0]);
                    for (int j = 0; j < SEGMENTS; j++) {
                        Vec3 a = c.joints()[j], b = c.joints()[j + 1];
                        assertTrue(Double.isFinite(b.x) && Double.isFinite(b.y) && Double.isFinite(b.z), "finite " + w + " " + bulge);
                        assertEquals(seg, a.distanceTo(b), 1.0E-9, "segment " + j + " to " + w + " bulge " + bulge);
                    }
                    assertEquals(1, c.hinge().length(), 1.0E-9);
                }
            }
        }
    }

    @Test
    void chainEndsOnTheWristWithinReach() {
        Vec3 s = Vec3.ZERO;
        for (Vec3 w : new Vec3[]{new Vec3(0, 0, -0.5), new Vec3(0.4, 0.4, 0), new Vec3(0, -0.6, 0.2)}) {
            Chain c = chain(s, w, new Vec3(0, 1, 0), new Vec3(1, 0, 0), 0.3);
            assertEquals(0, c.wrist().distanceTo(w), 1.0E-6, "ends on " + w);
        }
    }

    @Test
    void reachIsClampedAndPointsAtTheTarget() {
        Vec3 s = Vec3.ZERO;
        double seg = 0.5, length = SEGMENTS * seg;
        for (Vec3 w : new Vec3[]{new Vec3(0, 0, -10), new Vec3(3, 4, 0), new Vec3(0, -50, 0), new Vec3(0, 0, -length * 0.95)}) {
            Chain c = chain(s, w, new Vec3(0, 1, 0), new Vec3(1, 0, 0), seg);
            Vec3 end = c.wrist();
            assertTrue(end.length() <= 0.985 * length + 1.0E-9, "never past 98.5% of the chain: " + end.length());
            assertTrue(end.length() > 0.9 * length, "nearly straight toward a far target");
            assertEquals(1, end.normalize().dot(w.normalize()), 1.0E-9, "points at the target");
        }
        double prev = 0;
        for (double d = 0; d < 3 * length; d += 0.01) {
            double r = softReach(d, length);
            assertTrue(r >= prev - 1.0E-12, "monotonic");
            prev = r;
        }
        assertEquals(0.3, segment(0.63, 0.7, 4), 1.0E-9);
        assertEquals(MIN_SEGMENT, segment(0, 0.7, 4));
        assertEquals(2, segment(100, 0.9, 2));
    }

    @Test
    void shouldersMirror() {
        assertEquals(0.5, (shoulderX(LEFT) + shoulderX(RIGHT)) / 2, 1.0E-9);
        assertTrue(shoulderX(LEFT) < 0.5 && shoulderX(RIGHT) > 0.5);
    }

    @Test
    void idleIsDeterministicBoundedAndCalm() {
        for (int side : SIDES) {
            for (float t = 0; t < 500; t += 1.3F) {
                assertEquals(restWrist(side, t), restWrist(side, t));
                double b = breathe(t), g = gesture(t, side);
                assertTrue(b >= -1 && b <= 1);
                assertTrue(g >= 0 && g <= 1);
                assertEquals(breathe(t), breathe(t + BREATH_TICKS), 1.0E-4);
                assertEquals(gesture(t, side), gesture(t + GESTURE_TICKS, side), 1.0E-4);
                assertTrue(restWrist(side, t).distanceTo(restWrist(side, t + 1)) < 0.01, "slow: under 0.01 block per tick");
            }
        }
        assertTrue(BREATH_TICKS >= 60 && BREATH_TICKS <= 80, "breath 3-4 s");
        boolean apart = true;
        for (float t = 0; t < GESTURE_TICKS; t += 0.5F) apart &= gesture(t, LEFT) == 0 || gesture(t, RIGHT) == 0;
        assertTrue(apart, "the two grippers never flex together");
    }

    @Test
    void gestureIsMostlyStill() {
        int moving = 0, samples = 0;
        for (float t = 0; t < GESTURE_TICKS; t += 1, samples++) if (gesture(t, LEFT) > 0) moving++;
        assertTrue(moving <= GESTURE_LENGTH + 1 && moving > 0, "moving ticks " + moving + "/" + samples);
    }

    @Test
    void firstPersonEntryIsOutsideTheFrameOnItsOwnSide() {
        double[] aspects = {4.0 / 3, 16.0 / 9, 21.0 / 9};
        for (double fov = 30; fov <= 110; fov += 5) {
            for (double aspect : aspects) {
                for (int side : SIDES) {
                    Vec3 p = entry(side, fov, aspect);
                    assertFalse(inFrame(p, fov, aspect), "fov " + fov + " aspect " + aspect + " side " + side + ": " + p);
                    assertEquals(side, Math.signum(p.x));
                    assertTrue(p.y < 0, "below the eye");
                    assertEquals(ENTRY_DISTANCE, Math.hypot(p.x, p.z), 1.0E-9);
                }
            }
        }
    }

    @Test
    void inFrameSeesTheCentre() {
        assertTrue(inFrame(new Vec3(0, 0, 1), 70, 16.0 / 9));
        assertFalse(inFrame(new Vec3(0, 0, -1), 70, 16.0 / 9));
    }

    @Test
    void opacityIsClamped() {
        assertEquals(MIN_OPACITY, opacity(0));
        assertEquals(MIN_OPACITY, opacity(-3));
        assertEquals(1, opacity(2));
        assertEquals(0.5F, opacity(0.5), 1.0E-6);
        assertEquals(1, opacity(Double.NaN));
        assertEquals(1, opacity(Double.POSITIVE_INFINITY));
        assertEquals(255, alpha(1));
        assertEquals(Math.round(MIN_OPACITY * 255), alpha(0));
    }

    @Test
    void timingCurvesStayInRangeAndSettle() {
        for (float age = -2; age < 30; age += 0.25F) {
            double w = windUp(age), f = followThrough(age), fo = folded(age), fl = flexOpen(age);
            assertTrue(w >= 0 && w <= 1);
            assertTrue(f >= 0 && f <= 1);
            assertTrue(fo >= 0 && fo <= 1);
            assertTrue(fl >= 0 && fl <= 1);
        }
        assertEquals(0, windUp(WIND_UP_TICKS));
        assertEquals(0, followThrough(FOLLOW_TICKS));
        assertEquals(1, folded(0), 1.0E-9);
        assertEquals(0, folded(FLEX_TICKS));
        assertEquals(0, flexOpen(FLEX_TICKS));
    }

    @Test
    void mainHandActuatorIsOnTheMainArmSide() {
        assertEquals(RIGHT, side(true, true), "right-handed: the Right Actuator is the main hand");
        assertEquals(LEFT, side(true, false));
        assertEquals(LEFT, side(false, true), "left-handed: they swap");
        assertEquals(RIGHT, side(false, false));
    }

    @Test
    void aRetargetIsDoneWithinThreeTicksAtAnyFrameRate() {
        for (double tau : new double[]{AIM_TAU, ACT_TAU}) {
            for (int fps : new int[]{15, 20, 30, 60, 144, 240}) {
                double dt = 20.0 / fps, left = 1, prev = 1;
                double two = Double.NaN, three = Double.NaN;
                for (double t = dt; t <= RETARGET_TICKS + 1.0E-9; t += dt) {
                    left *= 1 - chase(dt, tau);
                    assertTrue(left >= 0 && left < prev, "closes in steadily, no overshoot");
                    prev = left;
                    if (Double.isNaN(two) && t >= 2 - 1.0E-9) two = left;
                    three = left;
                }
                assertTrue(three <= RETARGET_LEFT * (fps < 20 ? 3 : 1.5), fps + " fps: " + three + " of the way left after " + RETARGET_TICKS + " ticks");
                assertTrue(two <= 0.05, fps + " fps: on the new target by the time the first blow lands, left " + two);
            }
            assertEquals(Math.exp(-RETARGET_TICKS / tau), Math.pow(1 - chase(1.0 / 7, tau), RETARGET_TICKS * 7), 1.0E-9, "the same at any frame rate");
            assertTrue(Math.exp(-RETARGET_TICKS / tau) <= RETARGET_LEFT);
        }
        assertEquals(0, chase(0, AIM_TAU));
        assertEquals(0, chase(-1, AIM_TAU));
        assertTrue(chase(1000, BACK_TAU) <= 1);
    }

    @Test
    void aStrikeWindsUpHitsReboundsAndLoops() {
        assertEquals(READY_SWING, strikeSwing(0), 1.0E-9);
        assertEquals(READY_SWING, strikeSwing(1), 1.0E-9, "the end of a swing is the start of the next: held mining loops");
        assertEquals(READY_BACK, strikeBack(0), 1.0E-9);
        assertEquals(READY_BACK, strikeBack(1), 1.0E-9);
        assertEquals(-1, strikeSwing(STRIKE_WIND), 1.0E-9, "wound back");
        assertEquals(1, strikeBack(STRIKE_WIND), 1.0E-9);
        assertEquals(1, strikeSwing(STRIKE_IMPACT), 1.0E-9, "on the target");
        assertEquals(0, strikeBack(STRIKE_IMPACT), 1.0E-9);
        assertTrue(STRIKE_IMPACT < 0.5, "the impact falls inside the three ticks vanilla's mining swing shows before it restarts");
        double step = 0.001;
        for (double p = 0; p < 1; p += step) {
            double a = strikeSwing(p), b = strikeSwing(p + step);
            assertTrue(a >= -1 - 1.0E-9 && a <= 1 + 1.0E-9);
            assertTrue(Math.abs(b - a) < 0.03, "no pop at " + p);
            assertTrue(Math.abs(strikeBack(p + step) - strikeBack(p)) < 0.03, "no pop at " + p);
            if (p + step < STRIKE_WIND) assertTrue(b <= a + 1.0E-9, "winds back");
            else if (p > STRIKE_WIND && p + 2 * step < STRIKE_IMPACT) {
                assertTrue(b > a, "swings forward");
                assertTrue(strikeSwing(p + 2 * step) - b > b - a - 1.0E-9, "speeding up into the impact");
            } else if (p > STRIKE_IMPACT && p + step < 1) assertTrue(b <= a + 1.0E-9, "rebounds and comes back to ready");
        }
        assertEquals(0, thrust(0), 1.0E-9);
        assertEquals(1, thrust(THRUST_PEAK), 1.0E-9);
        assertEquals(0, thrust(1), 1.0E-9);
        for (double p = 0; p < 1; p += step) assertTrue(Math.abs(thrust(p + step) - thrust(p)) < 0.03);
    }

    @Test
    void theToolHeadLandsOnTheTargetAtTheImpactAndLeadsTheGrip() {
        Vec3[] targets = {new Vec3(0, 0, 4), new Vec3(1.5, -1.2, 0.8), new Vec3(-3, 2, 5), new Vec3(0, -1.6, 0.2)};
        Swing[] planes = {Swing.of(new Vec3(-0.62, 0.6, 0.5), new Vec3(0.7, 0.7, -0.25)), Swing.of(new Vec3(0, -0.62, 0.78), new Vec3(0, 0.8, -0.6)),
                Swing.of(new Vec3(0, 1, 0), new Vec3(0, 1, 0))};
        for (Vec3 target : targets) {
            for (Swing plane : planes) {
                assertEquals(1, plane.impact().length(), 1.0E-9);
                assertEquals(1, plane.back().length(), 1.0E-9);
                assertEquals(0, plane.impact().dot(plane.back()), 1.0E-9);
                for (double length : new double[]{TOOL_LENGTH * 0.6, TOOL_LENGTH}) {
                    Vec3 pull = plane.back().scale(0.1).subtract(plane.impact().scale(0.2));
                    Tool hit = strikeAt(target, plane, length, STRIKE_IMPACT, pull, 0);
                    assertEquals(0, hit.head(length).distanceTo(target), 1.0E-9, "head on the target at the impact");
                    Tool wound = strikeAt(target, plane, length, STRIKE_WIND, pull, 0);
                    assertTrue(wound.head(length).distanceTo(target) > length, "reared back before it: " + wound.head(length).distanceTo(target));
                    assertEquals(strikeAt(target, plane, length, 0, pull, 0).head(length), strikeAt(target, plane, length, 1, pull, 0).head(length));
                    double headWay = 0, gripWay = 0, prev = Double.MAX_VALUE;
                    Tool last = wound;
                    for (double p = STRIKE_WIND; p <= STRIKE_IMPACT + 1.0E-9; p += 0.005) {
                        Tool t = strikeAt(target, plane, length, Math.min(p, STRIKE_IMPACT), pull, 0);
                        assertEquals(1, t.axis().length(), 1.0E-9);
                        assertEquals(1, t.edge().length(), 1.0E-9);
                        assertEquals(0, t.axis().dot(t.edge()), 1.0E-9, "edge across the axis");
                        double left = t.head(length).distanceTo(target);
                        assertTrue(left <= prev + 1.0E-9, "the head only closes in during the strike");
                        prev = left;
                        Vec3 moved = t.head(length).subtract(last.head(length));
                        if (moved.length() > 1.0E-6) assertTrue(moved.normalize().dot(t.edge()) > 0.5, "the edge leads the head");
                        headWay += moved.length();
                        gripWay += t.grip().distanceTo(last.grip());
                        last = t;
                    }
                    assertTrue(headWay > 2 * gripWay, "the head travels farther than the grip: " + headWay + " vs " + gripWay);
                }
            }
        }
    }

    @Test
    void dualWieldStrikesInTurn() {
        assertEquals(0, miningSwing(0), 1.0E-9);
        assertEquals(0.5, miningSwing(MINING_CYCLE - 1), 1.0E-9);
        assertEquals(miningSwing(1.3), miningSwing(1.3 + MINING_CYCLE), 1.0E-9, "vanilla's mining swing restarts every " + MINING_CYCLE + " ticks");
        double main = impactTick(false), off = impactTick(true);
        assertEquals(MINING_CYCLE / 2, Math.abs(main - off), 0.02, "the two impacts are half a cycle apart");
        assertEquals(-1, dualMelee(0), "a single blow: the off hand waits a beat");
        assertEquals(-1, dualMelee(DUAL_BEAT - 0.01));
        assertEquals(0, dualMelee(DUAL_BEAT), 1.0E-9);
        assertTrue(dualMelee(DUAL_BEAT + SWING_TICKS - 0.01) < 1);
        assertEquals(-1, dualMelee(DUAL_BEAT + SWING_TICKS));
    }

    private static double impactTick(boolean off) {
        for (double t = 0; t < MINING_CYCLE; t += 0.005) {
            double a = off ? dualMining(t) : miningSwing(t), b = off ? dualMining(t + 0.005) : miningSwing(t + 0.005);
            if (a <= STRIKE_IMPACT && b > STRIKE_IMPACT) return t;
        }
        return Double.NaN;
    }

    private static final double FOV = 70, ASPECT = 16 / 9.0, MAX_SEGMENT = 2.2;
    private static final Vec3 LOOK = new Vec3(0, 0, 1), UP = new Vec3(0, 1, 0);

    /** View space, the eye at the origin: first person from the frame's edge, third person from the tank's top corner behind the head. */
    private static Vec3 root(int side, boolean first) {
        return first ? entry(side, FOV, ASPECT) : new Vec3(side * SHOULDER_X, 0.25, -0.4);
    }

    private static Chain solve(int side, boolean first, Vec3 grip, Vec3 axis, double along, double straight) {
        Vec3 palm = palm(root(side, first), grip, axis, LOOK, along);
        assertEquals(0, palm.dot(axis.normalize()), 1.0E-6, "the gripper closes square across the item");
        return solve(side, first, grip, palm, straight);
    }

    private static Chain solve(int side, boolean first, Vec3 grip, Vec3 palm, double straight) {
        Vec3 root = root(side, first);
        Hand hand = hand(root, grip, palm, 1);
        assertEquals(0, hand.wrist().add(hand.dir().scale(GRIP_LENGTH)).distanceTo(grip), 1.0E-9, "the fingers meet on the grip spot");
        Vec3 bulge = first ? new Vec3(side, 0.25, 0) : new Vec3(side * 0.7, 1, 0);
        Chain c = chain(root, hand.wrist(), bulge, new Vec3(side, 0.3, 0), segment(root.distanceTo(hand.wrist()), straight, MAX_SEGMENT));
        double seg = c.segment();
        assertTrue(seg >= MIN_SEGMENT - 1.0E-9 && seg <= MAX_SEGMENT + 1.0E-9, "segment " + seg);
        for (int j = 0; j < SEGMENTS; j++) assertEquals(seg, c.joints()[j].distanceTo(c.joints()[j + 1]), 1.0E-9, "every segment one length");
        for (int j = 0; j + 2 <= SEGMENTS; j++) {
            Vec3 in = c.joints()[j + 1].subtract(c.joints()[j]).normalize(), out = c.joints()[j + 2].subtract(c.joints()[j + 1]).normalize();
            assertTrue(Math.toDegrees(Math.acos(Math.min(1, in.dot(out)))) < 115, "no joint folds back on itself");
            assertTrue(c.joints()[j].distanceTo(c.joints()[j + 2]) > seg, "joints two apart never meet");
        }
        return c;
    }

    @Test
    void segmentsKeepOneLengthUnderEveryCarryAndUsePose() {
        for (int side : SIDES) {
            for (boolean first : new boolean[]{true, false}) {
                java.util.List<Grip> poses = new java.util.ArrayList<>();
                poses.add(carry(first));
                for (UseAnim anim : UseAnim.values()) for (float used : new float[]{0, 1.5F, 4, 11, 20, 47, 300}) poses.add(use(anim, first, used));
                for (Grip g : poses) {
                    Vec3 grip = new Vec3(side * g.at().x, g.at().y, g.at().z);
                    Vec3 axis = UP.scale(Math.cos(g.tilt())).add(LOOK.scale(Math.sin(g.tilt())));
                    Chain c = solve(side, first, grip, axis, g.along(), 0.8);
                    assertEquals(0, c.wrist().add(hand(root(side, first), grip, axis, LOOK, g.along(), 1).dir().scale(GRIP_LENGTH)).distanceTo(grip), 1.0E-6,
                            "the chain reaches the pose");
                    if (!first) continue;
                    assertTrue(Math.hypot(grip.x, grip.y) / grip.z >= 0.3, "a carried or used item stays off the crosshair: " + grip);
                    assertEquals(side, Math.signum(grip.x), "on its own side of the view");
                    for (Vec3 j : c.joints()) assertTrue(j.length() > 0.3, "no joint through the camera: " + j);
                }
                if (first) assertTrue(Math.hypot(BARE.x, BARE.y) / BARE.z >= 0.6, "the empty gripper waits low in the corner");
            }
        }
    }

    @Test
    void segmentsKeepOneLengthThroughAStrikeAndAThrust() {
        Vec3[] targets = {new Vec3(0, 0, 1.2), new Vec3(0, 0, 3), new Vec3(0, 0, 6), new Vec3(0.4, -1.5, 1.5), new Vec3(-2, 1.5, 3), new Vec3(0, 3, 0.3)};
        for (int side : SIDES) {
            for (boolean first : new boolean[]{true, false}) {
                Swing plane = first ? Swing.of(new Vec3(-side * 0.62, 0.6, 0.5), new Vec3(side * 0.7, 0.7, -0.25))
                        : Swing.of(new Vec3(0, -0.62, 0.78), new Vec3(0, 0.8, -0.6));
                for (Vec3 target : targets) {
                    Vec3 dir = target.subtract(root(side, first)).normalize();
                    Vec3 pull = dir.scale(-0.22).add(plane.back().scale(0.1));
                    Vec3 lastWrist = null;
                    for (double p = 0; p <= 1.0001; p += 0.02) {
                        Tool t = strikeAt(target, plane, TOOL_LENGTH, p, pull, first ? -0.85 * side : 0);
                        assertEquals(1, t.palm().length(), 1.0E-9);
                        Chain c = solve(side, first, t.grip(), t.palm(), 0.88);
                        if (lastWrist != null) assertTrue(c.wrist().distanceTo(lastWrist) < 0.2, "no pop in the strike at " + p);
                        lastWrist = c.wrist();
                        Vec3 grip = target.subtract(dir.scale(0.24 + 0.45 * (1 - thrust(p))));
                        Vec3 axis = UP.subtract(dir.scale(UP.dot(dir)));
                        solve(side, first, grip, axis.lengthSqr() < 1.0E-6 ? LOOK : axis, 0, 0.88);
                    }
                }
            }
        }
    }

    @Test
    void theGripperSquaresUpToTheItemOnlyAsItTakesHold() {
        Vec3 root = new Vec3(0.7, -0.3, 0.3), grip = new Vec3(0.6, -0.4, 0.9);
        Hand free = hand(root, grip, UP, LOOK, 0, 0);
        assertEquals(1, free.dir().dot(grip.subtract(root).normalize()), 1.0E-9, "nothing held: in line with the arm");
        Hand held = hand(root, grip, UP, LOOK, 0, 1);
        assertEquals(0, held.dir().dot(UP), 1.0E-9);
        Hand along = hand(root, grip, grip.subtract(root), LOOK, 0, 1);
        assertEquals(1, along.dir().length(), 1.0E-9, "an item along the arm still finds a side to be held from");
        assertEquals(0, along.dir().dot(grip.subtract(root).normalize()), 1.0E-6);
        for (double k = 0; k <= 1; k += 0.1) assertEquals(GRIP_LENGTH, hand(root, grip, UP, LOOK, 0.5, k).wrist().distanceTo(grip), 1.0E-9);
    }

    @Test
    void lifeCuesStayInRange() {
        assertEquals(0, flinch(0, 10));
        assertEquals(1, flinch(10, 10), 1.0E-9);
        assertTrue(flinch(5, 10) < 0.5, "a flinch eases off fast");
        assertEquals(0, brace(0.3));
        assertEquals(1, brace(1.5), 1.0E-9);
        assertEquals(0, lowAir(0.5));
        assertEquals(1, lowAir(0), 1.0E-9);
        for (float t = 0; t < 700; t += 0.7F) {
            double b = bump(t, 230, 18, 5);
            assertTrue(b >= 0 && b <= 1);
        }
        int busy = 0;
        for (int t = 0; t < 230; t++) if (bump(t, 230, 18, 5) > 0) busy++;
        assertTrue(busy <= 18, "a fidget is rare: " + busy + " of 230 ticks");
    }

    /** The old first-person transform for a standing body facing {@code yaw}: tank space to eye-relative world. */
    private static Vec3 standingBody(Vec3 tank, float yaw) {
        com.mojang.blaze3d.vertex.PoseStack ps = new com.mojang.blaze3d.vertex.PoseStack();
        ps.translate(0, -STANDING_EYE, 0);
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - yaw));
        TentacleRenderer.bodyToTank(ps);
        org.joml.Vector3f v = ps.last().pose().transformPosition((float) tank.x, (float) tank.y, (float) tank.z, new org.joml.Vector3f());
        return new Vec3(v.x, v.y, v.z);
    }

    @Test
    void eyeViewIsTheStandingBodyFacingTheLook() {
        for (float yaw : new float[]{0, 37, 90, 200, -120}) {
            double r = Math.toRadians(yaw);
            Vec3 right = new Vec3(-Math.cos(r), 0, -Math.sin(r)), up = new Vec3(0, 1, 0), ahead = new Vec3(-Math.sin(r), 0, Math.cos(r));
            for (int side : SIDES) {
                for (Vec3 tank : new Vec3[]{restWrist(side, 0), restWrist(side, 30), shoulder(side), new Vec3(0, 0, 0), new Vec3(1, 1, 1)}) {
                    Vec3 old = standingBody(tank, yaw), p = eyeView(tank);
                    Vec3 now = right.scale(p.x).add(up.scale(p.y)).add(ahead.scale(p.z));
                    assertTrue(old.distanceTo(now) < 1.0E-4, "yaw " + yaw + " side " + side + " " + tank + ": " + old + " vs " + now);
                }
            }
        }
    }

    @Test
    void firstPersonRestFoldedAndLimpStayOffScreen() {
        double[] aspects = {4.0 / 3, 16.0 / 9, 21.0 / 9};
        for (double fov = 30; fov <= 110; fov += 5) {
            for (double aspect : aspects) {
                for (int side : SIDES) {
                    Vec3 s = entry(side, fov, aspect);
                    Vec3 sh = shoulder(side);
                    for (float t = 0; t < BREATH_TICKS; t += 7) {
                        offScreen(s, eyeView(restWrist(side, t)), eyeDirection(restBulge(side)), restStraight(t), side, fov, aspect, "rest t " + t);
                    }
                    offScreen(s, eyeView(sh.add(side * 0.06, 0.18, 0.06)), eyeDirection(new Vec3(side, 0.4, 0.5)), 0.5, side, fov, aspect, "folded");
                    offScreen(s, eyeView(sh.add(side * 0.3, -0.62, -0.02)), eyeDirection(new Vec3(side, 0.25, 0.35)), 0.88, side, fov, aspect, "limp");
                }
            }
        }
    }

    private static void offScreen(Vec3 s, Vec3 wrist, Vec3 bulge, double straight, int side, double fov, double aspect, String what) {
        for (double reach = 4; reach <= 9; reach += 1) {
            Chain c = chain(s, wrist, bulge, new Vec3(side, 0.3, 0), segment(s.distanceTo(wrist), straight, (reach + 0.5) / SEGMENTS));
            Vec3[] j = c.joints();
            for (int i = 0; i + 1 < j.length; i++)
                for (int k = 0; k <= 4; k++) {
                    Vec3 p = j[i].lerp(j[i + 1], k / 4.0);
                    assertFalse(inFrame(p, fov, aspect), what + " fov " + fov + " aspect " + aspect + " side " + side + " reach " + reach + ": " + p);
                }
            Vec3 tip = wrist.add(c.wristDir().scale(GRIP_LENGTH));
            assertFalse(inFrame(tip, fov, aspect), what + " tip fov " + fov + " aspect " + aspect + " side " + side + ": " + tip);
        }
    }
}
