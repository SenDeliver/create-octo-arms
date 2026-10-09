package dev.tentaworks.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.tentaworks.rig.Tentacles;
import net.createmod.ponder.foundation.instruction.FadeIntoSceneInstruction;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Scripts a {@link RigPuppetElement} while the scene is built: body moves in 2-tick chunks and a small gait (the game's
 * rules: spread targets per body slot, at most two Steps at once as a diagonal pair, never fewer than two planted)
 * picks Grips on the given faces. Every change becomes a scene instruction, so rewinding replays it exactly.
 */
final class PuppetScript {
    /** An axis-aligned block face rectangle the Tentacles may grip (min/max equal along the normal axis). */
    record Face(Vec3 min, Vec3 max, Vec3 normal) {
        Vec3 project(Vec3 p) {
            double in = 0.2;
            return new Vec3(clamp(p.x, min.x, max.x, in), clamp(p.y, min.y, max.y, in), clamp(p.z, min.z, max.z, in));
        }

        private static double clamp(double v, double lo, double hi, double in) {
            if (hi - lo < 1.0E-6) return lo;
            return Math.max(lo + in, Math.min(hi - in, v));
        }
    }

    private record Target(Vec3 point, Face face) {
    }

    private static final int STEP = 5;
    private static final double STRIDE = 0.9;

    private final CreateSceneBuilder scene;
    private final RigPuppetElement puppet;
    private final double reach;
    private Vec3 body;
    private final float yaw;
    private final Vec3[] grip = new Vec3[Tentacles.SLOTS];
    private final Face[] gripFace = new Face[Tentacles.SLOTS];
    private final int[] stepEnd = new int[Tentacles.SLOTS];
    private int time;

    PuppetScript(CreateSceneBuilder scene, RigPuppetElement puppet, Vec3 body, float yaw, double reach) {
        this.scene = scene;
        this.puppet = puppet;
        this.body = body;
        this.yaw = yaw;
        this.reach = reach;
    }

    void show(int ticks) {
        scene.addInstruction(new FadeIn(ticks, puppet));
    }

    void idle(int ticks) {
        scene.idle(ticks);
        time += ticks;
    }

    /** Double Jump: all four Grips at once, then up to the standing height. */
    void activate(double height, List<Face> faces) {
        scene.addInstruction(PonderInstruction.simple(s -> puppet.active = true));
        Vec3 up = body.add(0, height, 0);
        Vec3 keep = body;
        body = up;
        for (int i = 0; i < Tentacles.SLOTS; i++) {
            Target t = target(i, faces, Vec3.ZERO);
            if (t != null) grip(i, t, 8);
        }
        body = keep;
        idle(6);
        move(up, 12, faces);
    }

    /** Lets go of every Grip and folds the Tentacles (Stowed). */
    void stow() {
        for (int i = 0; i < Tentacles.SLOTS; i++) {
            int slot = i;
            grip[i] = null;
            gripFace[i] = null;
            scene.addInstruction(PonderInstruction.simple(s -> puppet.slots[slot].set(null, null, 8)));
        }
        scene.addInstruction(PonderInstruction.simple(s -> puppet.active = false));
    }

    void move(Vec3 to, int ticks, List<Face> faces) {
        Vec3 from = body;
        Vec3 dir = to.subtract(from);
        dir = dir.lengthSqr() < 1.0E-6 ? Vec3.ZERO : dir.normalize();
        for (int k = 0; k < ticks; k += 2) {
            int d = Math.min(2, ticks - k);
            Vec3 next = from.lerp(to, (k + d) / (double) ticks);
            scene.addInstruction(new Move(puppet, next, d));
            body = next;
            gait(faces, dir);
            idle(d);
        }
    }

    private void grip(int slot, Target t, int ticks) {
        grip[slot] = t.point;
        gripFace[slot] = t.face;
        stepEnd[slot] = time + ticks;
        Vec3 p = t.point, n = t.face.normal;
        scene.addInstruction(PonderInstruction.simple(s -> puppet.slots[slot].set(p, n, ticks)));
    }

    private Vec3 centre() {
        return body.add(0, 1.0, 0);
    }

    private void gait(List<Face> faces, Vec3 dir) {
        int stepping = 0;
        for (int i = 0; i < Tentacles.SLOTS; i++) if (stepEnd[i] > time) stepping++;
        Vec3 c = centre();
        for (int i = 0; i < Tentacles.SLOTS; i++) {
            if (stepEnd[i] > time) continue;
            Target t = target(i, faces, dir);
            if (t == null) continue;
            boolean need = grip[i] == null || grip[i].distanceTo(c) > reach || grip[i].distanceTo(t.point) > STRIDE
                    || (gripFace[i] != t.face && grip[i].distanceTo(t.point) > 0.4);
            if (!need) continue;
            int planted = 0;
            for (int j = 0; j < Tentacles.SLOTS; j++) if (j != i && grip[j] != null && stepEnd[j] <= time) planted++;
            if (stepping >= 2 || planted < 2) continue;
            if (stepping == 1 && stepEnd[3 - i] <= time) continue;
            grip(i, t, STEP);
            stepping++;
        }
    }

    /** Body slot target (front-left, front-right, back-left, back-right = upper/lower shoulders), led along the move, pulled onto the nearest face in reach. */
    private Target target(int slot, List<Face> faces, Vec3 dir) {
        double r = Math.toRadians(yaw);
        Vec3 f = new Vec3(-Math.sin(r), 0, Math.cos(r)), l = new Vec3(Math.cos(r), 0, Math.sin(r));
        boolean left = slot % 2 == 0, upper = slot < 2;
        Vec3 c = centre();
        Vec3 want = c.add(l.scale(left ? 0.9 : -0.9)).add(f.scale(upper ? 0.7 : -0.5)).add(0, upper ? 0.7 : -1.3, 0).add(dir.scale(0.6));
        Target best = null;
        double bestD = Double.MAX_VALUE;
        for (Face face : faces) {
            Vec3 p = face.project(want);
            if (p.distanceTo(c) > reach) continue;
            double d = p.distanceTo(want);
            if (d < bestD) {
                bestD = d;
                best = new Target(p, face);
            }
        }
        return best;
    }

    private static final class FadeIn extends FadeIntoSceneInstruction<RigPuppetElement> {
        FadeIn(int ticks, RigPuppetElement element) {
            super(ticks, Direction.DOWN, element);
        }

        @Override
        protected void firstTick(PonderScene scene) {
            element.restart();
            super.firstTick(scene);
        }

        @Override
        protected Class<RigPuppetElement> getElementClass() {
            return RigPuppetElement.class;
        }
    }

    private static final class Move extends TickingInstruction {
        private final RigPuppetElement puppet;
        private final Vec3 to;
        private Vec3 from;

        Move(RigPuppetElement puppet, Vec3 to, int ticks) {
            super(false, ticks);
            this.puppet = puppet;
            this.to = to;
        }

        @Override
        protected void firstTick(PonderScene scene) {
            super.firstTick(scene);
            from = puppet.pos;
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);
            puppet.pos = from.lerp(to, 1.0 - (double) remainingTicks / totalTicks);
        }
    }
}
