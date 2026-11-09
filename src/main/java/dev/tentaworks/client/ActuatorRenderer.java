package dev.tentaworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.compat.Frame;
import dev.tentaworks.compat.ShipFrames;
import dev.tentaworks.rig.Actuators;
import dev.tentaworks.rig.RigTier;
import dev.tentaworks.rig.TentacleRigItem;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.Tags;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static dev.tentaworks.client.ActuatorPose.LEFT;
import static dev.tentaworks.client.ActuatorPose.RIGHT;
import static dev.tentaworks.client.TentacleRenderer.PX;

/**
 * The two Actuators of a fitted Rig on every wearer, Stowed or Active, chest or Back Slot: a hub on each top corner of the tank,
 * a fluid tube and a claw. At rest they rise above the shoulders, wider than the body, breathing with now and then a small
 * gesture. The Right Actuator reaches a Far Use target (third person: with the main-hand item at its tip), the Left one points
 * at a grabbable mob under the wearer's crosshair and holds a light target; both hold a heavy one or a Ship. A Grab winds up, a
 * Throw follows through, the first wear after Fitting unfolds and flexes them, no Air hangs them limp. In first person they
 * enter from the frame's sides. Per-player animation state ticks here; {@link ActuatorClient#get} is the input.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class ActuatorRenderer {
    private static final Int2ObjectMap<Anim> ANIMS = new Int2ObjectOpenHashMap<>();
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, TentaworksIds.id(TentaworksIds.BOSSES));
    private static final double BOSS_HEALTH = 100;
    private static final float GRAB_OPEN = 0.45F, REACH_OPEN = 0.55F, POINT_OPEN = 0.75F, THROW_OPEN = 0.9F, LIMP_OPEN = 0.05F;
    /** How far the Left Actuator uncurls toward a grabbable mob, and how far a reaching claw stops short of the target, blocks. */
    private static final double POINT_DISTANCE = 1.1, STANDOFF = 0.1, THROW_DISTANCE = 2.2;
    private static final float POINT_TICKS = 5, LIMP_TICKS = 10;
    /** Tube radius at the shoulder and at the wrist, hub half size, px. */
    private static final float ROOT_RADIUS = 1.3F, WRIST_RADIUS = 0.85F, HUB = 1.8F;
    private static final float FINGER = 5.5F, FINGER_SPREAD = 64F, KNUCKLE_TOP = 1.15F, KNUCKLE = 1.35F, HINGE = 1.1F, LENS = 1.3F, FINGER_HW = 0.45F;
    /** First person: tube rings and claws this close to the eye are not drawn, blocks. */
    private static final float NEAR_EYE = 0.25F;
    private static final int[] RINGS_PER_BLOCK = {6, 4, 3}, TUBE_SIDES = {8, 6, 5};
    private static final int MAX_RINGS = 64;

    /** One player's Actuator animation: 0..1 levels per action (this and last tick) and the points they aim at. */
    static final class Anim {
        boolean fitted;
        int flexStart = Integer.MIN_VALUE / 2, grabStart = Integer.MIN_VALUE / 2, throwStart = Integer.MIN_VALUE / 2;
        float far, farO, hold, holdO, both, bothO, point, pointO, limp, limpO;
        Vec3 farAt, pointAt, throwDir = Vec3.ZERO;
        boolean heavy, throwBoth;
        int heldId = -1;
        BlockPos ship;
        Direction shipFace = Direction.UP;
        final Vec3[] lastHold = new Vec3[2];

        Anim(boolean fitted) {
            this.fitted = fitted;
        }

        boolean holding() {
            return heldId >= 0 || ship != null;
        }

        static float lerp(float o, float v, float pt) {
            return (float) ActuatorPose.smooth(o + (v - o) * pt);
        }
    }

    /** A drawn Actuator in world space: shoulder, elbow, claw contact point, the way the claw looks, how open it is. */
    record Limb(Vec3 s, Vec3 e, Vec3 t, Vec3 look, float open) {
        Limb blend(Limb o, double k) {
            if (k <= 0) return this;
            if (k >= 1) return o;
            Vec3 l = look.lerp(o.look, k);
            return new Limb(s.lerp(o.s, k), e.lerp(o.e, k), t.lerp(o.t, k), l.lengthSqr() < 1.0E-6 ? o.look : l.normalize(), (float) Mth.lerp(k, open, o.open));
        }

        Limb withOpen(float open) {
            return new Limb(s, e, t, look, open);
        }
    }

    /** An Actuator in tank space, ready to draw. */
    private record Arm(Vector3f s, Vector3f e, Vector3f w, Vector3f t, Vector3f n, Vector3f side, float open, int sideSign) {
    }

    private record Sprites(TextureAtlasSprite skin, TextureAtlasSprite claw, TextureAtlasSprite lens, TextureAtlasSprite hub, TextureAtlasSprite cog) {
        static Sprites of(RigTier tier) {
            String s = tier == RigTier.NETHERITE ? "_netherite" : "";
            var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
            return new Sprites(atlas.apply(tex("tentacle_skin" + s)), atlas.apply(tex("tentacle_claw" + s)), atlas.apply(tex("tentacle_lens" + s)),
                    atlas.apply(tex("tentacle_hub" + s)), atlas.apply(tex("tentacle_cog" + s)));
        }

        private static ResourceLocation tex(String name) {
            return TentaworksIds.id("block/" + name);
        }
    }

    private ActuatorRenderer() {
    }

    static Anim anim(Player player) {
        return ANIMS.get(player.getId());
    }

    /** The main-hand item is drawn at the Right Actuator's tip (third person, no screen open), so the hand layer leaves it out. */
    public static boolean itemAtTip(LivingEntity entity) {
        if (!(entity instanceof Player player) || Minecraft.getInstance().screen != null) return false;
        Anim a = ANIMS.get(player.getId());
        return a != null && a.fitted && a.far >= 0.5F && !player.getMainHandItem().isEmpty();
    }

    /** A Throw by {@code player} along {@code dir}: the arms follow through and snap back. */
    static void thrown(Player player, Vec3 dir) {
        Anim a = ANIMS.get(player.getId());
        if (a == null) return;
        a.throwStart = player.tickCount;
        a.throwDir = dir.lengthSqr() < 1.0E-6 ? player.getViewVector(1.0F) : dir.normalize();
        a.throwBoth = a.heavy;
    }

    /** Culling: a wearer whose body is off screen still renders while an Actuator reaches or holds in view. */
    public static boolean inView(Player player, Frustum frustum) {
        Anim a = ANIMS.get(player.getId());
        if (a == null || !a.fitted) return false;
        AABB box = player.getBoundingBox().inflate(0.8, 0.6, 0.8);
        if (a.far > 0 && a.farAt != null) box = box.minmax(new AABB(a.farAt, a.farAt));
        if (a.hold > 0) {
            Entity held = a.heldId >= 0 ? player.level().getEntity(a.heldId) : null;
            if (held != null) box = box.minmax(held.getBoundingBox());
            for (Vec3 p : a.lastHold) if (p != null) box = box.minmax(new AABB(p, p));
        }
        return frustum.isVisible(box.inflate(0.5));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ANIMS.clear();
            return;
        }
        if (mc.isPaused()) return;
        ANIMS.int2ObjectEntrySet().removeIf(e -> !(mc.level.getEntity(e.getIntKey()) instanceof Player));
        for (AbstractClientPlayer p : mc.level.players()) tick(mc, p, ANIMS.computeIfAbsent(p.getId(), id -> new Anim(Actuators.fitted(p))));
    }

    private static void tick(Minecraft mc, Player p, Anim a) {
        boolean fitted = Actuators.fitted(p);
        if (fitted && !a.fitted) a.flexStart = p.tickCount;
        a.fitted = fitted;
        a.farO = a.far;
        a.holdO = a.hold;
        a.bothO = a.both;
        a.pointO = a.point;
        a.limpO = a.limp;
        if (!fitted) {
            a.far = a.hold = a.both = a.point = a.limp = 0;
            a.heldId = -1;
            a.ship = null;
            return;
        }
        ActuatorClient.State s = ActuatorClient.get(p);
        boolean limp = Actuators.limp(p);
        boolean holding = s.holding();
        if (holding && !a.holding()) a.grabStart = p.tickCount;
        if (holding) {
            a.heldId = s.heldId();
            a.ship = s.ship();
            a.shipFace = s.shipFace();
            a.heavy = s.heavy() || s.ship() != null;
        } else {
            a.heldId = -1;
            a.ship = null;
        }
        Vec3 far = s.farTarget();
        if (far != null) a.farAt = far;
        a.far = ActuatorPose.approach(a.far, far != null && !(holding && a.heavy) && !limp, ActuatorPose.REACH_TICKS);
        a.hold = ActuatorPose.approach(a.hold, holding && !limp, ActuatorPose.REACH_TICKS);
        a.both = ActuatorPose.approach(a.both, holding && a.heavy && !limp, ActuatorPose.REACH_TICKS);
        Vec3 point = p == mc.player && !holding && !limp ? pointTarget(mc.player) : null;
        if (point != null) a.pointAt = point;
        a.point = ActuatorPose.approach(a.point, point != null, POINT_TICKS);
        a.limp = ActuatorPose.approach(a.limp, limp, LIMP_TICKS);
    }

    /** Telegraph: a grabbable-looking mob under the crosshair within Reach while the main hand is empty (no packet). */
    private static Vec3 pointTarget(LocalPlayer p) {
        if (!p.getMainHandItem().isEmpty() || !Actuators.ready(p)) return null;
        if (!(ActuatorClient.aim(p, TentacleRigItem.reach(p)) instanceof EntityHitResult hit)) return null;
        Entity t = hit.getEntity();
        if (!(t instanceof LivingEntity living) || t instanceof Player || t.isPassenger() || t.isVehicle()) return null;
        if (t.getType().is(BOSSES) || t.getType().is(Tags.EntityTypes.BOSSES) || living.getMaxHealth() > BOSS_HEALTH) return null;
        return t.getBoundingBox().getCenter();
    }

    /**
     * Draws a fitted Rig's Actuators. {@code ps} is in tank space; {@code space} maps it to the world. Limb geometry first, then
     * the item at the tip, which takes other buffers.
     */
    static void draw(PoseStack ps, MultiBufferSource buffers, Player player, RigTier tier, float pt, TentacleRenderer.Space space) {
        if (!Actuators.fitted(player)) return;
        Sprites sp = Sprites.of(tier);
        VertexConsumer vc = LimbBuffer.get(buffers, space.firstPerson());
        float time = player.tickCount + pt;
        Anim a = ANIMS.get(player.getId());
        if (!space.world() || a == null) {
            for (int side : new int[]{LEFT, RIGHT}) drawArm(ps, vc, sp, restArm(side, time), null, space);
            return;
        }
        Matrix4f toTank = new Matrix4f(space.toCamera()).invert();
        Body b = Body.of(player, space, pt);
        Arm left = toTank(toTank, space, limb(LEFT, a, player, b, space, time, pt), LEFT);
        Arm right = toTank(toTank, space, limb(RIGHT, a, player, b, space, time, pt), RIGHT);
        boolean drewRight = false;
        if (left != null) drawArm(ps, vc, sp, left, player.level(), space);
        if (right != null) drewRight = drawArm(ps, vc, sp, right, player.level(), space);
        if (drewRight && !space.firstPerson() && itemAtTip(player)) drawItem(ps, buffers, player, right, space);
    }

    /** The wearer's frame this frame (world): shoulders, the body's out (per side) and up, and the reach limit. */
    private record Body(Vec3 left, Vec3 right, Vec3 rightDir, Vec3 up, Vec3 forward, double reach) {
        static Body of(Player player, TentacleRenderer.Space space, float pt) {
            Vec3 rightDir = vec(space.direction(new Vector3f(1, 0, 0)));
            Vec3 up = vec(space.direction(new Vector3f(0, 1, 0)));
            Vec3 forward = vec(space.direction(new Vector3f(0, 0, -1)));
            double reach = Math.max(1, TentacleRigItem.reach(player)) + 0.5;
            if (!space.firstPerson()) {
                return new Body(world(space, ActuatorPose.shoulder(LEFT)), world(space, ActuatorPose.shoulder(RIGHT)), rightDir, up, forward, reach);
            }
            Minecraft mc = Minecraft.getInstance();
            Camera cam = mc.gameRenderer.getMainCamera();
            Vec3 look = vec(cam.getLookVector()), camUp = vec(cam.getUpVector()), camRight = vec(cam.getLeftVector()).scale(-1);
            double fov = mc.options.fov().get();
            double aspect = mc.getWindow().getHeight() == 0 ? 16 / 9.0 : (double) mc.getWindow().getWidth() / mc.getWindow().getHeight();
            Vec3 eye = space.camera();
            return new Body(entry(eye, ActuatorPose.entry(LEFT, fov, aspect), camRight, camUp, look),
                    entry(eye, ActuatorPose.entry(RIGHT, fov, aspect), camRight, camUp, look), camRight, camUp, look, reach);
        }

        private static Vec3 entry(Vec3 eye, Vec3 local, Vec3 right, Vec3 up, Vec3 look) {
            return eye.add(right.scale(local.x)).add(up.scale(local.y)).add(look.scale(local.z));
        }

        Vec3 shoulder(int side) {
            return side < 0 ? left : right;
        }

        Vec3 out(int side) {
            return rightDir.scale(side);
        }
    }

    /** This frame's pose of one Actuator: Rest (with the Fitting flex), then pointing, Far Use, hold, throw and limp blended over it. */
    private static Limb limb(int side, Anim a, Player player, Body b, TentacleRenderer.Space space, float time, float pt) {
        Vec3 s = b.shoulder(side);
        Limb pose = rest(side, space, time, s);
        float flexAge = time - a.flexStart;
        double fold = ActuatorPose.folded(flexAge);
        if (fold > 0) pose = pose.blend(folded(side, space, s), fold);
        double flex = ActuatorPose.flexOpen(flexAge);
        if (flex > 0) pose = pose.withOpen((float) Mth.lerp(flex, pose.open(), 0.95F));
        if (side == LEFT) {
            float k = Anim.lerp(a.pointO, a.point, pt);
            if (k > 0 && a.pointAt != null) pose = pose.blend(toward(s, a.pointAt, Math.min(POINT_DISTANCE, a.pointAt.distanceTo(s) * 0.5), b, side, POINT_OPEN), k);
        } else {
            float k = Anim.lerp(a.farO, a.far, pt);
            if (k > 0 && a.farAt != null) {
                Vec3 d = a.farAt.subtract(s);
                double len = Math.max(0, d.length() - STANDOFF - ActuatorPose.hammer(player.getAttackAnim(pt)));
                pose = pose.blend(toward(s, a.farAt, Math.min(len, b.reach), b, side, REACH_OPEN), k);
            }
        }
        float hk = side == LEFT ? Anim.lerp(a.holdO, a.hold, pt) : Anim.lerp(a.bothO, a.both, pt);
        if (hk > 0) {
            Vec3 tip = holdPoint(side, a, player, b, s, pt);
            int i = side == LEFT ? 0 : 1;
            if (tip != null) a.lastHold[i] = tip;
            else tip = a.lastHold[i];
            if (tip != null) {
                tip = ActuatorPose.clampReach(s, tip, b.reach);
                double wind = ActuatorPose.windUp(time - a.grabStart);
                if (wind > 0) tip = tip.lerp(s.add(b.up.scale(0.35)).add(b.out(side).scale(0.25)).subtract(b.forward.scale(0.15)), wind);
                pose = pose.blend(at(s, tip, b, side, GRAB_OPEN), hk);
            }
        }
        if (side == LEFT || a.throwBoth) {
            double k = ActuatorPose.followThrough(time - a.throwStart);
            if (k > 0) pose = pose.blend(toward(s, s.add(a.throwDir), THROW_DISTANCE, b, side, THROW_OPEN), k);
        }
        float lk = Anim.lerp(a.limpO, a.limp, pt);
        if (lk > 0) pose = pose.blend(limpPose(side, space, s), lk);
        return pose;
    }

    private static Limb toward(Vec3 s, Vec3 target, double distance, Body b, int side, float open) {
        Vec3 d = target.subtract(s);
        if (d.lengthSqr() < 1.0E-8) d = b.forward;
        return at(s, s.add(d.normalize().scale(Math.max(0.2, distance))), b, side, open);
    }

    private static Limb at(Vec3 s, Vec3 tip, Body b, int side, float open) {
        Vec3 d = tip.subtract(s);
        Vec3 look = d.lengthSqr() < 1.0E-8 ? b.forward : d.normalize();
        return new Limb(s, ActuatorPose.elbow(s, tip, b.out(side), b.up), tip, look, open);
    }

    /** Where an Actuator takes hold this frame: the near side of a light target (Left), either flank of a heavy one, the hull face of a Ship. */
    private static Vec3 holdPoint(int side, Anim a, Player player, Body b, Vec3 s, float pt) {
        Level level = player.level();
        if (a.ship != null) {
            Frame f = ShipFrames.get().containing(level, a.ship);
            if (f == null) return null;
            Vec3 n = Vec3.atLowerCornerOf(a.shipFace.getNormal());
            return f.renderPose(pt).toWorld(Vec3.atCenterOf(a.ship).add(n.scale(0.5))).add(b.out(side).scale(0.4));
        }
        Entity t = a.heldId >= 0 ? level.getEntity(a.heldId) : null;
        if (t == null) return null;
        double w = t.getBbWidth();
        Vec3 centre = t.getPosition(pt).add(0, t.getBbHeight() / 2, 0);
        Vec3 toward = s.subtract(centre).multiply(1, 0, 1);
        Vec3 near = toward.lengthSqr() < 1.0E-6 ? Vec3.ZERO : toward.normalize();
        if (!a.heavy) return centre.add(near.scale(w * 0.5));
        return centre.add(near.scale(w * 0.3)).add(b.out(side).scale(w * 0.45));
    }

    private static Limb rest(int side, TentacleRenderer.Space space, float time, Vec3 s) {
        return new Limb(s, world(space, ActuatorPose.restElbow(side, time)), world(space, ActuatorPose.restTip(side, time)),
                vec(space.direction(vec3f(ActuatorPose.restLook(side, time)))), ActuatorPose.restOpen(side, time));
    }

    private static Limb folded(int side, TentacleRenderer.Space space, Vec3 s) {
        Vec3 sh = ActuatorPose.shoulder(side);
        return new Limb(s, world(space, sh.add(side * 0.1, 0.1, 0)), world(space, sh.add(side * 0.12, 0.18, 0.1)),
                vec(space.direction(new Vector3f(0, 1, 0))), 0);
    }

    private static Limb limpPose(int side, TentacleRenderer.Space space, Vec3 s) {
        Vec3 sh = ActuatorPose.shoulder(side);
        return new Limb(s, world(space, sh.add(side * 0.24, -0.15, 0.05)), world(space, sh.add(side * 0.18, -0.7, 0.1)),
                vec(space.direction(new Vector3f(0, -1, 0.15F))), LIMP_OPEN);
    }

    /** Rest Pose straight in tank space, for the inventory preview (no world). */
    private static Arm restArm(int side, float time) {
        Vector3f s = vec3f(ActuatorPose.shoulder(side));
        Vector3f t = vec3f(ActuatorPose.restTip(side, time));
        Vector3f n = vec3f(ActuatorPose.restLook(side, time)).negate();
        return arm(s, vec3f(ActuatorPose.restElbow(side, time)), t, n, ActuatorPose.restOpen(side, time), side);
    }

    private static Arm toTank(Matrix4f toTank, TentacleRenderer.Space space, Limb l, int side) {
        Vec3 c = space.camera();
        Vector3f s = toTank.transformPosition((float) (l.s().x - c.x), (float) (l.s().y - c.y), (float) (l.s().z - c.z), new Vector3f());
        Vector3f e = toTank.transformPosition((float) (l.e().x - c.x), (float) (l.e().y - c.y), (float) (l.e().z - c.z), new Vector3f());
        Vector3f t = toTank.transformPosition((float) (l.t().x - c.x), (float) (l.t().y - c.y), (float) (l.t().z - c.z), new Vector3f());
        Vector3f n = toTank.transformDirection((float) -l.look().x, (float) -l.look().y, (float) -l.look().z, new Vector3f());
        if (!s.isFinite() || !e.isFinite() || !t.isFinite() || !n.isFinite() || n.lengthSquared() < 1.0E-8F) return null;
        return arm(s, e, t, n.normalize(), l.open(), side);
    }

    private static Arm arm(Vector3f s, Vector3f e, Vector3f t, Vector3f n, float open, int side) {
        Vector3f w = new Vector3f(t).fma(TentacleRenderer.PAD + TentacleRenderer.STEM, n);
        Vector3f across = new Vector3f(side, 0, 0);
        Vector3f sideAxis = new Vector3f(across).fma(-across.dot(n), n);
        if (sideAxis.lengthSquared() < 1.0E-4F) sideAxis.set(0, 0, 1).fma(-n.z, n);
        return new Arm(s, e, w, t, n, sideAxis.normalize(), open, side);
    }

    /** One Actuator: hub on the tank (not in first person), tube, claw. False when nothing was drawn. */
    private static boolean drawArm(PoseStack ps, VertexConsumer vc, Sprites sp, Arm a, Level level, TentacleRenderer.Space space) {
        PoseStack.Pose pose = ps.last();
        if (space.firstPerson() && pose.pose().transformPosition(a.w, new Vector3f()).length() < NEAR_EYE) return false;
        int lod = Mth.clamp(space.lod(), 0, 2);
        boolean detail = lod < 1;
        int ls = space.light(level, a.s, null), le = space.light(level, a.e, a.s), lw = space.light(level, new Vector3f(a.w).fma(0.2F, a.n), a.e);
        if (lod < 2 && !space.firstPerson()) {
            ps.pushPose();
            ps.translate(a.s.x, a.s.y, a.s.z);
            TentacleRenderer.Lit lit = TentacleRenderer.Lit.flat(ls);
            TentacleRenderer.box(ps.last(), vc, sp.hub(), lit, space, -HUB, HUB, -HUB, HUB, -HUB, HUB, TentacleRenderer.Mode.BLOCK);
            if (detail) TentacleRenderer.decalX(ps.last(), vc, sp.cog(), ls, space, a.sideSign * (HUB + 0.05F), HUB * 2);
            ps.popPose();
        }
        float length = a.s.distance(a.e) + a.e.distance(a.w);
        int rings = Mth.clamp((int) Math.ceil(length * RINGS_PER_BLOCK[lod]) + 1, 4, MAX_RINGS);
        Vector3f shoulderDir = space.firstPerson() ? null : new Vector3f(a.sideSign * 0.3F, 1, 0).normalize();
        TentacleSpline.Ring[] r = TentacleSpline.sample(a.s, a.e, a.w, shoulderDir, new Vector3f(a.n).negate(), new Vector3f(0, 0, 1), rings);
        tube(ps.last(), vc, sp.skin(), space, r, ls, le, lw, TUBE_SIDES[lod]);
        if (space.firstPerson() && pose.pose().transformPosition(a.t, new Vector3f()).length() < NEAR_EYE * 2) return true;
        TentacleRenderer.frame(ps, a.t, a.n, a.side);
        claw(ps, vc, sp, lw, a.open, detail, space);
        ps.popPose();
        return true;
    }

    /** The tube along the rings, tapering shoulder → wrist, skin tiled once per half block from the wrist end, lit along the arm. */
    private static void tube(PoseStack.Pose p, VertexConsumer vc, TextureAtlasSprite s, TentacleRenderer.Space space, TentacleSpline.Ring[] r,
                             int ls, int le, int lw, int sides) {
        float total = r[r.length - 1].along();
        if (!Float.isFinite(total) || total <= 1.0E-5F) return;
        float[] cos = new float[sides + 1], sin = new float[sides + 1];
        for (int j = 0; j <= sides; j++) {
            double ang = Math.PI * 2 * j / sides;
            cos[j] = (float) Math.cos(ang);
            sin[j] = (float) Math.sin(ang);
        }
        boolean open = false;
        for (int i = 0; i + 1 < r.length; i++) {
            TentacleSpline.Ring a = r[i], b = r[i + 1];
            float ra = radius(a.along() / total), rb = radius(b.along() / total);
            float[] keep = cut(p, space, a, b);
            if (keep == null) {
                if (open) cap(p, vc, s, r[i - 1], a, radius(r[i - 1].along() / total), ra, 1, ls, le, lw, sides, cos, sin);
                open = false;
                continue;
            }
            float k0 = keep[0], k1 = keep[1];
            if (k0 > 0 || (i > 0 && !open)) cap(p, vc, s, a, b, ra, rb, k0, ls, le, lw, sides, cos, sin);
            if (k1 < 1) cap(p, vc, s, a, b, ra, rb, k1, ls, le, lw, sides, cos, sin);
            open = k1 >= 1;
            float vA = frac((total - b.along()) * 2) * 16, vB = vA + (b.along() - a.along()) * 32;
            float kSplit = vB > 16 ? 1 - (16 - vA) / (vB - vA) : -1;
            if (kSplit > k0 && kSplit < k1) {
                band(p, vc, s, a, b, ra, rb, k0, kSplit, vA, vB, ls, le, lw, sides, cos, sin);
                band(p, vc, s, a, b, ra, rb, kSplit, k1, vA, vB, ls, le, lw, sides, cos, sin);
            } else {
                band(p, vc, s, a, b, ra, rb, k0, k1, vA, vB, ls, le, lw, sides, cos, sin);
            }
        }
    }

    private static float frac(float x) {
        return x - Mth.floor(x);
    }

    private static float radius(float along) {
        return (ROOT_RADIUS + (WRIST_RADIUS - ROOT_RADIUS) * along) * PX;
    }

    private static float[] cut(PoseStack.Pose p, TentacleRenderer.Space space, TentacleSpline.Ring a, TentacleSpline.Ring b) {
        if (!space.firstPerson()) return new float[]{0, 1};
        return TentacleMath.outsideSphere(p.pose().transformPosition(a.c(), new Vector3f()), p.pose().transformPosition(b.c(), new Vector3f()), NEAR_EYE);
    }

    private static void band(PoseStack.Pose p, VertexConsumer vc, TextureAtlasSprite s, TentacleSpline.Ring a, TentacleSpline.Ring b, float ra, float rb,
                             float k0, float k1, float vA, float vB, int ls, int le, int lw, int sides, float[] cos, float[] sin) {
        if (k1 - k0 < 1.0E-5F) return;
        Vector3f c0 = new Vector3f(a.c()).lerp(b.c(), k0), c1 = new Vector3f(a.c()).lerp(b.c(), k1);
        Vector3f x0 = new Vector3f(a.x()).lerp(b.x(), k0).normalize(), x1 = new Vector3f(a.x()).lerp(b.x(), k1).normalize();
        Vector3f z0 = new Vector3f(a.z()).lerp(b.z(), k0).normalize(), z1 = new Vector3f(a.z()).lerp(b.z(), k1).normalize();
        float r0 = ra + (rb - ra) * k0, r1 = ra + (rb - ra) * k1;
        float v0 = Mth.clamp(vA + (1 - k0) * (vB - vA), 0, 16), v1 = Mth.clamp(vA + (1 - k1) * (vB - vA), 0, 16);
        if (v0 > 16 - 1.0E-3F && v1 > 16 - 1.0E-3F) {
            v0 -= 16;
            v1 -= 16;
        }
        int light = segLight(ls, le, lw, a.seg() + (b.seg() - a.seg()) * (k0 + k1) * 0.5F);
        for (int j = 0; j < sides; j++) {
            float cA = cos[j], sA = sin[j], cB = cos[j + 1], sB = sin[j + 1];
            Vector3f p00 = new Vector3f(c0).fma(r0 * cA, x0).fma(r0 * sA, z0);
            Vector3f p01 = new Vector3f(c0).fma(r0 * cB, x0).fma(r0 * sB, z0);
            Vector3f p10 = new Vector3f(c1).fma(r1 * cA, x1).fma(r1 * sA, z1);
            Vector3f p11 = new Vector3f(c1).fma(r1 * cB, x1).fma(r1 * sB, z1);
            Vector3f nrm = new Vector3f(x0).mul((cA + cB) * 0.5F).fma((sA + sB) * 0.5F, z0).normalize();
            float u0 = s.getU(j / (float) sides), u1 = s.getU((j + 1) / (float) sides);
            float tv0 = s.getV(v0 / 16f), tv1 = s.getV(v1 / 16f);
            TentacleRenderer.vertex(vc, p, p10.x, p10.y, p10.z, u0, tv1, light, nrm.x, nrm.y, nrm.z);
            TentacleRenderer.vertex(vc, p, p00.x, p00.y, p00.z, u0, tv0, light, nrm.x, nrm.y, nrm.z);
            TentacleRenderer.vertex(vc, p, p01.x, p01.y, p01.z, u1, tv0, light, nrm.x, nrm.y, nrm.z);
            TentacleRenderer.vertex(vc, p, p11.x, p11.y, p11.z, u1, tv1, light, nrm.x, nrm.y, nrm.z);
        }
    }

    private static void cap(PoseStack.Pose p, VertexConsumer vc, TextureAtlasSprite s, TentacleSpline.Ring a, TentacleSpline.Ring b, float ra, float rb,
                            float k, int ls, int le, int lw, int sides, float[] cos, float[] sin) {
        Vector3f c = new Vector3f(a.c()).lerp(b.c(), k);
        Vector3f x = new Vector3f(a.x()).lerp(b.x(), k).normalize(), z = new Vector3f(a.z()).lerp(b.z(), k).normalize();
        Vector3f n = new Vector3f(x).cross(z).normalize();
        float rad = ra + (rb - ra) * k;
        int light = segLight(ls, le, lw, a.seg() + (b.seg() - a.seg()) * k);
        float uc = s.getU(0.5F), vcen = s.getV(0.5F);
        for (int j = 0; j < sides; j++) {
            Vector3f pa = new Vector3f(c).fma(rad * cos[j], x).fma(rad * sin[j], z);
            Vector3f pb = new Vector3f(c).fma(rad * cos[j + 1], x).fma(rad * sin[j + 1], z);
            float ua = s.getU(0.5F + 0.5F * cos[j]), va = s.getV(0.5F + 0.5F * sin[j]), ub = s.getU(0.5F + 0.5F * cos[j + 1]), vb = s.getV(0.5F + 0.5F * sin[j + 1]);
            TentacleRenderer.vertex(vc, p, c.x, c.y, c.z, uc, vcen, light, n.x, n.y, n.z);
            TentacleRenderer.vertex(vc, p, c.x, c.y, c.z, uc, vcen, light, n.x, n.y, n.z);
            TentacleRenderer.vertex(vc, p, pa.x, pa.y, pa.z, ua, va, light, n.x, n.y, n.z);
            TentacleRenderer.vertex(vc, p, pb.x, pb.y, pb.z, ub, vb, light, n.x, n.y, n.z);
        }
    }

    private static int segLight(int ls, int le, int lw, float seg) {
        return seg <= 1 ? TentacleMath.lerpLight(ls, le, Mth.clamp(seg, 0, 1)) : TentacleMath.lerpLight(le, lw, Mth.clamp(seg - 1, 0, 1));
    }

    /**
     * The claw in the contact frame (+Y = against the way it looks, X = side): a knuckle capping the tube, the glowing lens on its
     * underside, four slim fingers that spread {@code open}.
     */
    private static void claw(PoseStack ps, VertexConsumer vc, Sprites sp, int light, float open, boolean detail, TentacleRenderer.Space space) {
        float y0 = TentacleRenderer.PAD / PX, y1 = (TentacleRenderer.PAD + TentacleRenderer.STEM) / PX + 0.5F;
        knuckle(ps.last(), vc, sp.claw(), light, y0, KNUCKLE, y1, KNUCKLE_TOP, detail ? 8 : 6);
        float lr = LENS * PX, ly = TentacleRenderer.PAD - 0.02F * PX;
        TentacleRenderer.quad(vc, ps.last(), sp.lens(), 0xF000F0, 0, -1, 0, 0, 16, 0, 16, -lr, ly, lr, lr, ly, lr, lr, ly, -lr, -lr, ly, -lr);
        float spread = FINGER_SPREAD * Mth.clamp(open, 0, 1);
        float hw = detail ? FINGER_HW : FINGER_HW + 0.1F;
        TentacleRenderer.Lit lit = TentacleRenderer.Lit.flat(light);
        for (int f = 0; f < 4; f++) {
            float ax = f == 0 ? 1 : f == 1 ? -1 : 0, az = f == 2 ? 1 : f == 3 ? -1 : 0;
            ps.pushPose();
            ps.translate(ax * HINGE * PX, TentacleRenderer.PAD + 0.4F * PX, az * HINGE * PX);
            ps.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(-spread), -az, 0, ax));
            TentacleRenderer.box(ps.last(), vc, sp.claw(), lit, space, -hw, hw, -FINGER, 0.3F, -hw, hw, TentacleRenderer.Mode.BLOCK);
            if (detail) TentacleRenderer.box(ps.last(), vc, sp.hub(), lit, space, -hw * 0.7F, hw * 0.7F, -FINGER - 0.7F, -FINGER, -hw * 0.7F, hw * 0.7F, TentacleRenderer.Mode.BLOCK);
            ps.popPose();
        }
    }

    private static void knuckle(PoseStack.Pose p, VertexConsumer vc, TextureAtlasSprite s, int light, float y0, float r0, float y1, float r1, int sides) {
        float Y0 = y0 * PX, Y1 = y1 * PX, R0 = r0 * PX, R1 = r1 * PX;
        float v0 = s.getV(4 / 16f), v1 = s.getV(12 / 16f), uc = s.getU(0.5f), vc0 = s.getV(0.5f);
        for (int j = 0; j < sides; j++) {
            double a = Math.PI * 2 * j / sides, b = Math.PI * 2 * (j + 1) / sides;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a), cb = (float) Math.cos(b), sb = (float) Math.sin(b);
            float nx = (ca + cb) * 0.5F, nz = (sa + sb) * 0.5F;
            float u0 = s.getU(j / (float) sides), u1 = s.getU((j + 1) / (float) sides);
            TentacleRenderer.vertex(vc, p, R1 * ca, Y1, R1 * sa, u0, v0, light, nx, 0, nz);
            TentacleRenderer.vertex(vc, p, R0 * ca, Y0, R0 * sa, u0, v1, light, nx, 0, nz);
            TentacleRenderer.vertex(vc, p, R0 * cb, Y0, R0 * sb, u1, v1, light, nx, 0, nz);
            TentacleRenderer.vertex(vc, p, R1 * cb, Y1, R1 * sb, u1, v0, light, nx, 0, nz);
            float ua = s.getU(0.5f + 0.5f * ca), va = s.getV(0.5f + 0.5f * sa), ub = s.getU(0.5f + 0.5f * cb), vb = s.getV(0.5f + 0.5f * sb);
            TentacleRenderer.vertex(vc, p, 0, Y0, 0, uc, vc0, light, 0, -1, 0);
            TentacleRenderer.vertex(vc, p, 0, Y0, 0, uc, vc0, light, 0, -1, 0);
            TentacleRenderer.vertex(vc, p, R0 * cb, Y0, R0 * sb, ub, vb, light, 0, -1, 0);
            TentacleRenderer.vertex(vc, p, R0 * ca, Y0, R0 * sa, ua, va, light, 0, -1, 0);
            TentacleRenderer.vertex(vc, p, 0, Y1, 0, uc, vc0, light, 0, 1, 0);
            TentacleRenderer.vertex(vc, p, 0, Y1, 0, uc, vc0, light, 0, 1, 0);
            TentacleRenderer.vertex(vc, p, R1 * ca, Y1, R1 * sa, ua, va, light, 0, 1, 0);
            TentacleRenderer.vertex(vc, p, R1 * cb, Y1, R1 * sb, ub, vb, light, 0, 1, 0);
        }
    }

    /** The main-hand item between the Right Actuator's fingers, as a hand holds it (third person display). */
    private static void drawItem(PoseStack ps, MultiBufferSource buffers, Player player, Arm arm, TentacleRenderer.Space space) {
        ItemStack stack = player.getMainHandItem();
        TentacleRenderer.frame(ps, new Vector3f(arm.t).fma(PX, arm.n), new Vector3f(arm.n).negate(), arm.side);
        ps.mulPose(Axis.XP.rotationDegrees(-90));
        ps.mulPose(Axis.YP.rotationDegrees(180));
        ps.translate(0, 0.06F, 0);
        int light = space.light(player.level(), arm.t, arm.w);
        Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, ps, buffers,
                player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
        ps.popPose();
    }

    private static Vec3 world(TentacleRenderer.Space space, Vec3 tank) {
        return space.toWorld(vec3f(tank));
    }

    private static Vec3 vec(Vector3f v) {
        return new Vec3(v.x, v.y, v.z);
    }

    private static Vector3f vec3f(Vec3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }
}
