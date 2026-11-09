package dev.tentaworks.rig;

import dev.tentaworks.rig.Hold.Status;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a held mob sits (low on the left, out of the crosshair), the lift out of the floor, and when a hold ends. */
class HoldTest {
    private static final Vec3 EYE = new Vec3(0, 65.62, 0);
    private static final double[][] MOBS = {{0.9, 0.9}, {0.6, 1.95}, {0.9, 1.4}, {1.4, 0.9}, {0.9, 1.3}, {1.4, 2.7}, {1.3964844, 1.6}, {1.5, 0.88}};

    private static AABB box(Vec3 centre, double w, double h) {
        return AABB.ofSize(centre, w, h, w);
    }

    /** The plan's 0.35 at every yaw and pitch: the box is axis-aligned, so the offset uses its half-width across the turned view. */
    @Test
    void offTheCrosshairByAtLeastAThirdOfABlock() {
        for (float yaw = -180; yaw < 180; yaw += 7.5F)
            for (float pitch = -90; pitch <= 90; pitch += 7.5F) {
                Vec3 look = Vec3.directionFromRotation(pitch, yaw);
                for (double[] m : MOBS) {
                    Vec3 c = Hold.point(EYE, look, yaw, m[0], m[1]);
                    String at = "yaw " + yaw + " pitch " + pitch + " mob " + m[0] + "x" + m[1];
                    assertTrue(box(c, m[0], m[1]).inflate(0.35).clip(EYE, EYE.add(look.scale(16))).isEmpty(), at);
                    assertTrue(c.subtract(EYE).dot(look) > 1.0, at + ": ahead of the eye");
                }
            }
    }

    @Test
    void lowOnTheLeft() {
        Vec3 c = Hold.point(EYE, new Vec3(0, 0, 1), 0, 0.9, 0.9);
        assertEquals(Hold.AHEAD + 0.45, c.z, 1.0E-6);
        Vec3 diagonal = Hold.point(EYE, Vec3.directionFromRotation(0, 45), 45, 0.9, 0.9).subtract(EYE);
        assertEquals(Hold.LEFT + 0.45 * Math.sqrt(2), diagonal.dot(new Vec3(Math.cos(Math.PI / 4), 0, Math.sin(Math.PI / 4))), 1.0E-5, "a diagonal yaw: wider across the view");
        assertEquals(Hold.LEFT + 0.45, c.x, 1.0E-6, "facing south (+z) the left is +x");
        assertEquals(EYE.y - (Hold.DOWN + 0.45 - Hold.RAISE), c.y, 1.0E-6);
    }

    @Test
    void straightUpOrDownStillLeftOfTheCamera() {
        for (float pitch : new float[]{-90, 90}) {
            Vec3 look = Vec3.directionFromRotation(pitch, 0);
            Vec3 c = Hold.point(EYE, look, 0, 0.9, 0.9);
            assertTrue(Double.isFinite(c.x) && Double.isFinite(c.y) && Double.isFinite(c.z));
            assertEquals(Hold.LEFT + 0.45, c.x, 1.0E-6);
        }
        Vec3 c = Hold.point(EYE, Vec3.ZERO, 0, 0.9, 0.9);
        assertTrue(c.z > 0, "no look: ahead along the yaw");
    }

    /** A zombie held by a wearer standing on the floor would have its feet 0.58 below it: lifted to the first free step. */
    @Test
    void liftOutOfTheFloor() {
        double floor = 64;
        Vec3 c = Hold.point(EYE, new Vec3(0, 0, 1), 0, 0.6, 1.95);
        double feet = c.y - 1.95 / 2;
        assertTrue(feet < floor);
        double y = Hold.lift(feet, 1.95, at -> at >= floor);
        assertTrue(y >= floor && y < floor + Hold.LIFT_STEP, "lifted to " + y);
        assertEquals(feet, Hold.lift(feet, 1.95, at -> true), "fits where it is");
        assertTrue(Double.isNaN(Hold.lift(feet, 1.95, at -> at >= floor + 3)), "no room within its height: stays where it is");
    }

    /** Mob and Creeper load with the Pinned hook and the swell accessor merged in (a target that moved fails here, not in game). */
    @Test
    void mixinsApplied() throws ClassNotFoundException {
        ClassLoader loader = getClass().getClassLoader();
        Class<?> mob = Class.forName("net.minecraft.world.entity.Mob", false, loader);
        assertTrue(Arrays.stream(mob.getDeclaredMethods()).anyMatch(m -> m.getName().contains("pinned")));
        Class<?> creeper = Class.forName("net.minecraft.world.entity.monster.Creeper", false, loader);
        assertTrue(Arrays.stream(creeper.getDeclaredMethods()).anyMatch(m -> m.getName().contains("setSwell")));
    }

    @Test
    void farRule() {
        assertFalse(Hold.far(5.0, 4.0));
        assertTrue(Hold.far(5.01, 4.0));
    }

    @Test
    void releaseConditions() {
        Status ok = new Status(true, true, true, false, false, true, 0);
        assertNull(Hold.ends(ok), "Stowed, Setting Down, a Ride, a glide or Falling are not in the status: the hold goes on");
        assertEquals("death", Hold.ends(new Status(false, true, true, false, false, true, 0)));
        assertEquals("rig_off", Hold.ends(new Status(true, false, true, false, false, true, 0)));
        assertEquals("crawl", Hold.ends(new Status(true, true, false, true, false, true, 0)));
        assertEquals("air", Hold.ends(new Status(true, true, false, false, false, true, 0)));
        assertEquals("gone", Hold.ends(new Status(true, true, true, false, true, true, 0)));
        assertEquals("dimension", Hold.ends(new Status(true, true, true, false, false, false, 0)));
        assertNull(Hold.ends(new Status(true, true, true, false, false, true, Hold.FAR_TICKS - 1)));
        assertEquals("far", Hold.ends(new Status(true, true, true, false, false, true, Hold.FAR_TICKS)));
        assertEquals("death", Hold.ends(new Status(false, false, false, true, true, false, 9)), "the first reason wins");
    }
    @Test
    void aHeldShipPointStaysWhereItWasInTheView() {
        Vec3 look = Vec3.directionFromRotation(10, -90);
        Vec3 held = new Vec3(4.0, 64.2, 0.6);
        Vec3 rel = Hold.shipRel(EYE, look, -90, held, 4.5);
        assertTrue(Hold.shipPoint(EYE, look, -90, rel).distanceTo(held) < 1.0E-3, "unchanged view, unchanged point");
        for (float turn = -180; turn <= 180; turn += 15) {
            Vec3 l = Vec3.directionFromRotation(10, -90 + turn);
            Vec3 p = Hold.shipPoint(EYE, l, -90 + turn, rel);
            assertEquals(held.distanceTo(EYE), p.distanceTo(EYE), 1.0E-3, "turn " + turn + ": same distance");
            assertEquals(held.y, p.y, 1.0E-3, "turn " + turn + ": same height");
        }
        Vec3 turned = Hold.shipPoint(EYE, Vec3.directionFromRotation(10, -180), -180, rel);
        assertEquals(0.6, turned.x, 1.0E-3, "a quarter turn north swings it round the eye, still 0.6 to the right");
        assertEquals(-4.0, turned.z, 1.0E-3);
        Vec3 up = Hold.shipPoint(EYE, Vec3.directionFromRotation(-30, -90), -90, rel);
        assertTrue(up.y > held.y + 1.5, "looking up lifts it");
    }

    @Test
    void aHeldShipPointIsNoFartherThanTheLimit() {
        Vec3 look = new Vec3(1, 0, 0);
        Vec3 rel = Hold.shipRel(EYE, look, -90, EYE.add(9, 0, 0), 4.5);
        assertEquals(4.5, rel.length(), 1.0E-9);
        assertEquals(4.5, rel.x, 1.0E-9);
        assertEquals(2.0, Hold.shipRel(EYE, look, -90, EYE.add(2, 0, 0), 4.5).length(), 1.0E-9);
    }

    @Test
    void aHeldShipTurnsItsYawWithTheWearerOnly() {
        Quaterniond start = new Quaterniond().rotateX(0.3).rotateZ(0.2);
        Vector3d facing = start.transform(new Vector3d(0, 0, -1));
        Quaterniond q = Hold.shipTurn(start, 90);
        Vector3d now = q.transform(new Vector3d(0, 0, -1));
        Vector3d want = new Quaterniond().rotateY(-Math.PI / 2).transform(new Vector3d(facing));
        assertEquals(0, now.distance(want), 1.0E-9, "the face that pointed at the wearer still does");
        assertEquals(facing.y, now.y, 1.0E-9, "pitch and roll stay");
        Vector3d n = Hold.shipTurn(new Quaterniond(), 90).transform(new Vector3d(0, 0, -1));
        assertEquals(1, n.x, 1.0E-9, "facing south, a wearer at -z turned to look west (-x) still sees the face: it now points east at them");
        assertEquals(0, Hold.shipTurn(start, 360).transform(new Vector3d(0, 0, -1)).distance(facing), 1.0E-9);
    }
}
