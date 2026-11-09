package dev.tentaworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import com.simibubi.create.content.equipment.zapper.ZapperItem;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.compat.Frame;
import dev.tentaworks.compat.ShipFrames;
import dev.tentaworks.rig.Actuators;
import dev.tentaworks.rig.FarUse;
import dev.tentaworks.rig.RigTier;
import dev.tentaworks.rig.TentacleRigItem;
import dev.tentaworks.rig.WornRig;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.Tags;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static dev.tentaworks.client.ActuatorPose.LEFT;
import static dev.tentaworks.client.ActuatorPose.RIGHT;
import static dev.tentaworks.client.TentacleRenderer.PX;

/**
 * The two Actuators of a fitted Rig on every wearer, Stowed or Active, chest or Back Slot, drawn from the Tentacles' own parts
 * and tier textures: a hub on each top corner of the tank, three rigid telescoping segments (sleeve, collar, rod) joined by hubs,
 * and a three-finger gripper. At rest they curl above the shoulders, breathing slowly. The Right Actuator carries the main-hand
 * item whenever it is ready ({@link #carries}), strikes, pushes and pulls it in for the hand's swings and uses, and reaches a Far
 * Use target with it; the Left one points at a grabbable mob under the wearer's crosshair and holds a light target; both hold a
 * heavy one or a Ship. A Grab winds up, a Throw snaps out and returns slowly, the first wear after Fitting unfolds and flexes
 * them, no Air hangs them limp. In first person they enter from the frame's sides; a carrying Right Actuator is drawn in the hand
 * pass in place of the main hand, except while it reaches into the world. Per-player animation state ticks here; {@link ActuatorClient#get} is the input.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class ActuatorRenderer {
    private static final Int2ObjectMap<Anim> ANIMS = new Int2ObjectOpenHashMap<>();
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, TentaworksIds.id(TentaworksIds.BOSSES));
    private static final double BOSS_HEALTH = 100;
    private static final float GRAB_OPEN = 0.75F, REACH_OPEN = 0.7F, POINT_OPEN = 0.55F, THROW_OPEN = 1F, LIMP_OPEN = 0.35F, CARRY_OPEN = 0.12F;
    /** How far the Left Actuator uncurls toward a grabbable mob, how far a reaching tip stops short of the target, the throw follow-through, blocks. */
    private static final double POINT_DISTANCE = 1.1, STANDOFF = 0.1, THROW_DISTANCE = 1.6;
    /** Gripper length past the wrist, blocks (world). */
    private static final double GRIP_REACH = 0.3;
    /** How straight each pose's chain is (wrist distance / chain length). */
    private static final double POINT_STRAIGHT = 0.85, FAR_STRAIGHT = 0.95, HOLD_STRAIGHT = 0.9, HEAVY_STRAIGHT = 0.8, THROW_STRAIGHT = 0.97,
            LIMP_STRAIGHT = 0.88, CARRY_STRAIGHT = 0.8, FOLD_STRAIGHT = 0.5;
    /** Third person: the carried item in front of the face, from the eye along the head's look, right and up, blocks. */
    private static final double CARRY_AHEAD = 0.62, CARRY_RIGHT = 0.4, CARRY_UP = -0.3;
    /** Third person: the carried item follows the head's pitch up to this far up or down, degrees (an arm does not reach behind the head). */
    private static final float CARRY_PITCH = 50;
    /** First person: the carried item's spot in the hand pass (camera space, 70 degree view), blocks. */
    private static final double HAND_AHEAD = 0.9, HAND_RIGHT = 0.5, HAND_UP = -0.46;
    /** First person: the carried item's size against the third-person one (the hand pass draws it close to the eye). */
    private static final float HAND_ITEM_SCALE = 0.6F;
    /** Vertical field of view of the hand pass, degrees. */
    private static final double HAND_FOV = 70;
    /** Part sizes (px): hubs, sleeve, collar and rod half widths from the shoulder segment to the wrist one, about 2/3 of a Tentacle's. */
    private static final float ROOT_HUB = 1.4F, WRIST_HUB = 0.95F;
    private static final float[] JOINT_HUB = {1.15F, 1.05F}, SLEEVE = {1.0F, 0.85F, 0.72F}, COLLAR = {1.3F, 1.12F, 0.95F}, ROD = {0.66F, 0.56F, 0.48F};
    /** Longest sleeve (blocks); past it a segment grows only by its rod sliding out. */
    private static final float SLEEVE_MAX = 0.42F;
    /** Gripper (px): palm half width and length, finger hinge radius, half width, first and second joint lengths. */
    private static final float PALM = 1.15F, PALM_LEN = 1.7F, HINGE_R = 0.75F, FINGER_HW = 0.4F, PROX = 2.4F, DIST = 1.9F;
    /** First person: parts this close to the eye are not drawn, blocks. */
    private static final float NEAR_EYE = 0.25F;
    private static float worldM11 = (float) (1 / Math.tan(Math.toRadians(HAND_FOV) / 2));
    private static boolean placing;

    /** A 0..1 level easing out to on and back to off ({@link ActuatorPose#level}), set once per tick. */
    static final class Tween {
        boolean on;
        float from, since = -1000;

        void set(boolean on, float time) {
            if (on == this.on) return;
            from = (float) at(time);
            this.on = on;
            since = time;
        }

        double at(float time) {
            return ActuatorPose.level(from, on, time - since);
        }
    }

    /** One player's Actuator animation: a level per action and the points they aim at. */
    static final class Anim {
        boolean fitted;
        int flexStart = Integer.MIN_VALUE / 2, grabStart = Integer.MIN_VALUE / 2, throwStart = Integer.MIN_VALUE / 2;
        final Tween far = new Tween(), hold = new Tween(), both = new Tween(), point = new Tween(), limp = new Tween(), carry = new Tween();
        Vec3 farAt, farFrom, pointAt, throwDir = Vec3.ZERO;
        float farSince;
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

        Vec3 farPoint(float time) {
            if (farFrom == null || farAt == null) return farAt;
            return farFrom.lerp(farAt, ActuatorPose.easeOut((time - farSince) / ActuatorPose.REACH_TICKS));
        }
    }

    /** What an Actuator aims for: its wrist, the way its chain bulges, how straight, how open the gripper, how far the carried item tips forward. */
    record Aim(Vec3 wrist, Vec3 bulge, double straight, float open, double tilt) {
        Aim blend(Aim o, double k) {
            if (k <= 0) return this;
            if (k >= 1) return o;
            return new Aim(wrist.lerp(o.wrist, k), bulge.lerp(o.bulge, k), Mth.lerp(k, straight, o.straight), (float) Mth.lerp(k, open, o.open),
                    Mth.lerp(k, tilt, o.tilt));
        }

        Aim withOpen(float open) {
            return new Aim(wrist, bulge, straight, open, tilt);
        }

        Aim withWrist(Vec3 wrist) {
            return new Aim(wrist, bulge, straight, open, tilt);
        }
    }

    /** An Actuator in tank space, ready to draw: joints root to wrist, hinge axis, gripper opening, the item's up and tilt. */
    private record Drawn(Vector3f[] j, Vector3f hinge, float open, int side, Vector3f front, float tilt) {
        Vector3f wrist() {
            return j[j.length - 1];
        }

        Vector3f dir() {
            return new Vector3f(j[j.length - 1]).sub(j[j.length - 2]).normalize();
        }
    }

    private ActuatorRenderer() {
    }

    static Anim anim(Player player) {
        return ANIMS.get(player.getId());
    }

    /**
     * The Right Actuator carries the main-hand item instead of the hand: fitted, not limp, something in the main hand, no Extendo
     * Grip in either hand (Create lifts the reach itself), not an item Create draws in first person itself (Potato Cannon,
     * Worldshaper), not a map, and the Right Actuator not busy with a heavy hold.
     */
    public static boolean carries(LivingEntity entity) {
        if (!(entity instanceof Player player) || !Actuators.ready(player)) return false;
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || stack.getItem() instanceof MapItem || stack.getItem() instanceof PotatoCannonItem || stack.getItem() instanceof ZapperItem)
            return false;
        if (FarUse.extendo(player)) return false;
        ActuatorClient.State s = ActuatorClient.get(player);
        return !(s.holding() && (s.heavy() || s.ship() != null));
    }

    /** A Throw by {@code player} along {@code dir}: the arms snap out along it and come back slowly. */
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
        if (a.far.on && a.farAt != null) box = box.minmax(new AABB(a.farAt, a.farAt));
        if (a.hold.on) {
            Entity held = a.heldId >= 0 ? player.level().getEntity(a.heldId) : null;
            if (held != null) box = box.minmax(held.getBoundingBox());
            for (Vec3 p : a.lastHold) if (p != null) box = box.minmax(new AABB(p, p));
        }
        return frustum.isVisible(box.inflate(0.5));
    }

    /** The world pass's projection this frame, so the hand pass can match where things land on screen. */
    static void worldProjection(Matrix4f projection) {
        if (Float.isFinite(projection.m11()) && projection.m11() > 0) worldM11 = projection.m11();
    }

    /** Hand pass over world pass size of the same camera-space offset on screen (1 at a 70 degree view). */
    private static double handScale() {
        return worldM11 * Math.tan(Math.toRadians(HAND_FOV) / 2);
    }

    /** First person: the carrying Right Actuator goes to the hand pass (no hand pass with the GUI hidden or while scoping; not while it reaches for a Far Use). */
    static boolean inHandPass(Player player) {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player && mc.options.getCameraType().isFirstPerson() && !mc.options.hideGui && !player.isScoping() && carries(player)
                && !reachingFar(player);
    }

    /** A Far Use reach goes into the world: drawn in the world pass, where blocks and mobs in front of it hide it. */
    private static boolean reachingFar(Player player) {
        Anim a = ANIMS.get(player.getId());
        return a != null && a.far.on;
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

    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.isAttack()) placing = false;
        else if (event.isUseItem()) placing = true;
    }

    private static void tick(Minecraft mc, Player p, Anim a) {
        boolean fitted = Actuators.fitted(p);
        if (fitted && !a.fitted) a.flexStart = p.tickCount;
        a.fitted = fitted;
        float time = p.tickCount;
        if (!fitted) {
            for (Tween l : new Tween[]{a.far, a.hold, a.both, a.point, a.limp, a.carry}) {
                l.on = false;
                l.from = 0;
            }
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
        if (far != null) {
            if (a.farAt != null && a.far.at(time) > 0.05 && far.distanceToSqr(a.farAt) > 0.0025) {
                a.farFrom = a.farPoint(time);
                a.farSince = time;
            } else if (a.farAt == null || a.far.at(time) <= 0.05) {
                a.farFrom = null;
            }
            a.farAt = far;
        }
        a.far.set(far != null && !(holding && a.heavy) && !limp, time);
        a.hold.set(holding && !limp, time);
        a.both.set(holding && a.heavy && !limp, time);
        Vec3 point = p == mc.player && !holding && !limp ? pointTarget(mc.player) : null;
        if (point != null) a.pointAt = point;
        a.point.set(point != null, time);
        a.limp.set(limp, time);
        a.carry.set(carries(p), time);
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
     * First person: the carrying Right Actuator and its item in place of the main hand, in the hand pass (no wall clips it, F1
     * hides it like a hand). The hand pass has its own field of view; the world-to-camera transform is scaled so the arm lands on
     * screen exactly where the world pass would draw it.
     */
    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.getCameraEntity() != player || !inHandPass(player)) return;
        if (!(WornRig.of(player).item() instanceof TentacleRigItem rig)) return;
        event.setCanceled(true);
        float pt = event.getPartialTick();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 c = cam.getPosition();
        PoseStack world = new PoseStack();
        TentacleLayer.firstPersonTank(world, player, pt, c);
        TentacleRenderer.Space space = new TentacleRenderer.Space(new Matrix4f(world.last().pose()), c, true, true, 0xF000F0, 0);
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        float k = (float) handScale();
        ps.scale(k, k, 1);
        ps.mulPose(cam.rotation().conjugate(new Quaternionf()));
        TentacleLayer.firstPersonTank(ps, player, pt, c);
        TentacleRenderer.timed(() -> draw(ps, event.getMultiBufferSource(), player, rig.tier(), pt, space, RIGHT));
        ps.popPose();
    }

    /** Draws a fitted Rig's Actuators: both, or only {@code only} ({@link ActuatorPose#LEFT} / {@link ActuatorPose#RIGHT}, 0 both). */
    static void draw(PoseStack ps, MultiBufferSource buffers, Player player, RigTier tier, float pt, TentacleRenderer.Space space, int only) {
        if (!Actuators.fitted(player)) return;
        TentacleRenderer.Sprites sp = TentacleRenderer.Sprites.of(TentacleRenderer.Style.of(tier));
        VertexConsumer vc = LimbBuffer.get(buffers, space.firstPerson());
        float time = player.tickCount + pt;
        Anim a = ANIMS.get(player.getId());
        boolean item = carries(player) && (!space.firstPerson() || only == RIGHT
                || only == 0 && reachingFar(player) && !Minecraft.getInstance().options.hideGui && !player.isScoping());
        Drawn[] arms = new Drawn[2];
        if (!space.world() || a == null) {
            for (int side : new int[]{LEFT, RIGHT}) arms[side < 0 ? 0 : 1] = restInTank(side, time);
        } else {
            Matrix4f toTank = new Matrix4f(space.toCamera()).invert();
            Body b = Body.of(player, space, pt);
            for (int side : new int[]{LEFT, RIGHT}) {
                if (only != 0 && only != side) continue;
                Vec3 s = b.shoulder(side);
                Aim aim = aim(side, a, player, b, space, time, pt);
                double seg = ActuatorPose.segment(s.distanceTo(aim.wrist()), aim.straight(), b.maxSegment);
                ActuatorPose.Chain chain = ActuatorPose.chain(s, aim.wrist(), aim.bulge(), b.out(side).add(b.up.scale(0.3)), seg);
                arms[side < 0 ? 0 : 1] = toTank(toTank, space, chain, aim, side, b.headUp);
            }
        }
        for (int i = 0; i < 2; i++) {
            if (arms[i] == null || (only != 0 && only != (i == 0 ? LEFT : RIGHT))) continue;
            boolean tipShown = drawArm(ps, vc, sp, arms[i], player.level(), space);
            if (i == 1 && item && tipShown) drawItem(ps, buffers, player, arms[i], space);
        }
    }

    /** The wearer's frame this frame (world): shoulders, the body's out (per side) and up, the head's look basis and the longest segment. */
    private record Body(Vec3 left, Vec3 right, Vec3 rightDir, Vec3 up, Vec3 forward, Vec3 eye, Vec3 look, Vec3 headRight, Vec3 headUp,
                        boolean first, double handScale, double maxSegment) {
        static Body of(Player player, TentacleRenderer.Space space, float pt) {
            Vec3 rightDir = vec(space.direction(new Vector3f(1, 0, 0)));
            Vec3 up = vec(space.direction(new Vector3f(0, 1, 0)));
            Vec3 forward = vec(space.direction(new Vector3f(0, 0, -1)));
            double maxSegment = (Math.max(1, TentacleRigItem.reach(player)) + 0.5) / ActuatorPose.SEGMENTS;
            Vec3 eye = player.getEyePosition(pt);
            Vec3 look = player.getViewVector(pt);
            double yaw = Math.toRadians(player.getViewYRot(pt));
            Vec3 headRight = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vec3 headUp = headRight.cross(look).normalize();
            if (!space.firstPerson()) {
                return new Body(world(space, ActuatorPose.shoulder(LEFT)), world(space, ActuatorPose.shoulder(RIGHT)), rightDir, up, forward,
                        eye, look, headRight, headUp, false, 1, maxSegment);
            }
            Minecraft mc = Minecraft.getInstance();
            Camera cam = mc.gameRenderer.getMainCamera();
            Vec3 camLook = vec(cam.getLookVector()), camUp = vec(cam.getUpVector()), camRight = vec(cam.getLeftVector()).scale(-1);
            double fov = mc.options.fov().get();
            double aspect = mc.getWindow().getHeight() == 0 ? 16 / 9.0 : (double) mc.getWindow().getWidth() / mc.getWindow().getHeight();
            Vec3 c = space.camera();
            return new Body(entry(c, ActuatorPose.entry(LEFT, fov, aspect), camRight, camUp, camLook),
                    entry(c, ActuatorPose.entry(RIGHT, fov, aspect), camRight, camUp, camLook), camRight, camUp, camLook,
                    c, camLook, camRight, camUp, true, ActuatorRenderer.handScale(), maxSegment);
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

    /** This frame's aim of one Actuator: Rest (with the Fitting flex), then carrying, pointing, Far Use, hold, throw and limp blended over it. */
    private static Aim aim(int side, Anim a, Player player, Body b, TentacleRenderer.Space space, float time, float pt) {
        Vec3 s = b.shoulder(side);
        Aim pose = rest(side, space, time);
        float flexAge = time - a.flexStart;
        double fold = ActuatorPose.folded(flexAge);
        if (fold > 0) pose = pose.blend(folded(side, space), fold);
        double flex = ActuatorPose.flexOpen(flexAge);
        if (flex > 0) pose = pose.withOpen((float) Mth.lerp(flex, pose.open(), 0.95F));
        boolean carrying = side == RIGHT && a.carry.on;
        if (side == RIGHT) {
            double ck = a.carry.at(time);
            if (ck > 0) pose = pose.blend(carry(player, b, s, side, pt), ck);
        }
        if (side == LEFT) {
            double k = a.point.at(time);
            if (k > 0 && a.pointAt != null)
                pose = pose.blend(toward(s, a.pointAt, Math.min(POINT_DISTANCE, a.pointAt.distanceTo(s) * 0.5), b, side, POINT_STRAIGHT, POINT_OPEN), k);
        } else {
            double k = a.far.at(time);
            Vec3 at = a.farPoint(time);
            if (k > 0 && at != null) {
                Vec3 d = at.subtract(s);
                double len = Math.max(0.2, d.length() - STANDOFF - ActuatorPose.hammer(player.getAttackAnim(pt)));
                pose = pose.blend(toward(s, at, len, b, side, FAR_STRAIGHT, carrying ? CARRY_OPEN : REACH_OPEN), k);
            }
        }
        double hk = side == LEFT ? a.hold.at(time) : a.both.at(time);
        if (hk > 0) {
            Vec3 tip = holdPoint(side, a, player, b, s, pt);
            int i = side == LEFT ? 0 : 1;
            if (tip != null) a.lastHold[i] = tip;
            else tip = a.lastHold[i];
            if (tip != null) {
                double wind = ActuatorPose.windUp(time - a.grabStart);
                if (wind > 0) tip = tip.lerp(s.add(b.up.scale(0.35)).add(b.out(side).scale(0.25)).subtract(b.forward.scale(0.15)), wind);
                double straight = a.heavy ? HEAVY_STRAIGHT : HOLD_STRAIGHT;
                Vec3 bulge = a.heavy ? b.up.add(b.out(side).scale(0.4)) : b.up.scale(0.6).add(b.out(side));
                pose = pose.blend(reach(s, tip, bulge, straight, GRAB_OPEN), hk);
            }
        }
        if (side == LEFT || a.throwBoth) {
            double k = ActuatorPose.followThrough(time - a.throwStart);
            if (k > 0) pose = pose.blend(toward(s, s.add(a.throwDir), THROW_DISTANCE, b, side, THROW_STRAIGHT, THROW_OPEN), k);
        }
        double lk = a.limp.at(time);
        if (lk > 0) pose = pose.blend(limpPose(side, space), lk);
        return pose;
    }

    /**
     * The Right Actuator carrying the main-hand item: held in front of the face on the right (first person: where the hand holds
     * it), struck forward on an attack swing, pushed on a place, pulled in toward the eye while the item is used.
     */
    private static Aim carry(Player player, Body b, Vec3 s, int side, float pt) {
        boolean first = b.first;
        double k = b.handScale;
        Vec3 look = b.look, up = b.headUp;
        if (!first) {
            look = Vec3.directionFromRotation(Mth.clamp(player.getViewXRot(pt), -CARRY_PITCH, CARRY_PITCH), player.getViewYRot(pt));
            up = b.headRight.cross(look).normalize();
        }
        Vec3 tip = first ? b.eye.add(look.scale(HAND_AHEAD)).add(b.headRight.scale(HAND_RIGHT / k)).add(up.scale(HAND_UP / k))
                : b.eye.add(look.scale(CARRY_AHEAD)).add(b.headRight.scale(CARRY_RIGHT)).add(up.scale(CARRY_UP));
        double tilt = 0;
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            float used = player.getTicksUsingItem() + pt;
            double in = ActuatorPose.easeOut(used / 6);
            Vec3 near = b.eye.add(look.scale(first ? 0.5 : 0.42)).add(b.headRight.scale(first ? 0.14 / k : 0.16)).add(up.scale(first ? -0.18 / k : -0.1));
            tip = tip.lerp(near, 0.6 * in);
            UseAnim anim = player.getUseItem().getUseAnimation();
            if (anim == UseAnim.EAT || anim == UseAnim.DRINK) tip = tip.add(up.scale(0.025 * Math.sin(used * 1.3)));
        } else if (player.swingingArm == InteractionHand.MAIN_HAND && player.swinging) {
            float swing = player.getAttackAnim(pt);
            if (player == Minecraft.getInstance().player && placing) {
                tip = tip.add(look.scale(ActuatorPose.push(swing)));
            } else {
                Vec3 st = ActuatorPose.strike(swing);
                tip = tip.add(look.scale(st.x)).add(up.scale(st.y));
                tilt = st.z;
            }
        }
        Vec3 bulge = first ? b.out(side).add(b.up.scale(0.25)) : b.up.add(b.out(side).scale(0.7));
        Aim aim = reach(s, tip, bulge, CARRY_STRAIGHT, CARRY_OPEN);
        return new Aim(aim.wrist(), aim.bulge(), aim.straight(), aim.open(), tilt);
    }

    /** Aim the gripper's tip at {@code tip}: the wrist sits a gripper's length short of it on the line from the root. */
    private static Aim reach(Vec3 s, Vec3 tip, Vec3 bulge, double straight, float open) {
        Vec3 d = tip.subtract(s);
        double len = d.length();
        Vec3 wrist = len < 1.0E-6 ? tip : s.add(d.scale(Math.max(0.05, len - GRIP_REACH) / len));
        return new Aim(wrist, bulge, straight, open, 0);
    }

    private static Aim toward(Vec3 s, Vec3 target, double distance, Body b, int side, double straight, float open) {
        Vec3 d = target.subtract(s);
        if (d.lengthSqr() < 1.0E-8) d = b.forward;
        return reach(s, s.add(d.normalize().scale(Math.max(0.2, distance))), b.up.scale(0.5).add(b.out(side)), straight, open);
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

    private static Aim rest(int side, TentacleRenderer.Space space, float time) {
        return new Aim(world(space, ActuatorPose.restWrist(side, time)), vec(space.direction(vec3f(ActuatorPose.restBulge(side)))),
                ActuatorPose.restStraight(time), ActuatorPose.restOpen(side, time), 0);
    }

    private static Aim folded(int side, TentacleRenderer.Space space) {
        Vec3 sh = ActuatorPose.shoulder(side);
        return new Aim(world(space, sh.add(side * 0.06, 0.18, 0.06)), vec(space.direction(new Vector3f(side, 0.4F, 0.5F))), FOLD_STRAIGHT, 0, 0);
    }

    private static Aim limpPose(int side, TentacleRenderer.Space space) {
        Vec3 sh = ActuatorPose.shoulder(side);
        return new Aim(world(space, sh.add(side * 0.3, -0.62, -0.02)), vec(space.direction(new Vector3f(side, 0.25F, 0.35F))), LIMP_STRAIGHT, LIMP_OPEN, 0);
    }

    /** Rest Pose straight in tank space, for the inventory preview (no world). */
    private static Drawn restInTank(int side, float time) {
        Vec3 s = ActuatorPose.shoulder(side), w = ActuatorPose.restWrist(side, time);
        double seg = ActuatorPose.segment(s.distanceTo(w), ActuatorPose.restStraight(time), 4);
        ActuatorPose.Chain chain = ActuatorPose.chain(s, w, ActuatorPose.restBulge(side), new Vec3(side, 0.3, 0), seg);
        Vector3f[] j = new Vector3f[chain.joints().length];
        for (int i = 0; i < j.length; i++) j[i] = vec3f(chain.joints()[i]);
        return new Drawn(j, vec3f(chain.hinge()), ActuatorPose.restOpen(side, time), side, new Vector3f(0, 1, 0), 0);
    }

    private static Drawn toTank(Matrix4f toTank, TentacleRenderer.Space space, ActuatorPose.Chain chain, Aim aim, int side, Vec3 headUp) {
        Vec3 c = space.camera();
        Vec3[] w = chain.joints();
        Vector3f[] j = new Vector3f[w.length];
        for (int i = 0; i < w.length; i++) {
            j[i] = toTank.transformPosition((float) (w[i].x - c.x), (float) (w[i].y - c.y), (float) (w[i].z - c.z), new Vector3f());
            if (!j[i].isFinite()) return null;
        }
        Vector3f hinge = toTank.transformDirection(vec3f(chain.hinge()), new Vector3f());
        Vector3f front = toTank.transformDirection(vec3f(headUp), new Vector3f());
        if (!hinge.isFinite() || hinge.lengthSquared() < 1.0E-8F || !front.isFinite() || front.lengthSquared() < 1.0E-8F) return null;
        return new Drawn(j, hinge.normalize(), aim.open(), side, front.normalize(), (float) aim.tilt());
    }

    /** One Actuator: shoulder hub on the tank (not in first person), three segments with joint hubs, the wrist and gripper. False when the gripper was not drawn. */
    private static boolean drawArm(PoseStack ps, VertexConsumer vc, TentacleRenderer.Sprites sp, Drawn d, Level level, TentacleRenderer.Space space) {
        int lod = Mth.clamp(space.lod(), 0, 2);
        boolean detail = lod < 1, hubs = lod < 2;
        Vector3f[] j = d.j();
        int n = j.length;
        int[] light = new int[n];
        for (int i = 0; i < n; i++) light[i] = space.light(level, j[i], i > 0 ? j[i - 1] : null);
        if (hubs && !space.firstPerson()) {
            ps.pushPose();
            ps.translate(j[0].x, j[0].y, j[0].z);
            TentacleRenderer.box(ps.last(), vc, sp.hub(), TentacleRenderer.Lit.flat(light[0]), space, -ROOT_HUB, ROOT_HUB, -ROOT_HUB, ROOT_HUB, -ROOT_HUB, ROOT_HUB, TentacleRenderer.Mode.BLOCK);
            if (detail) TentacleRenderer.decalX(ps.last(), vc, sp.cog(), light[0], space, d.side() * (ROOT_HUB + 0.05F), ROOT_HUB * 2.2F);
            ps.popPose();
        }
        for (int i = 0; i + 1 < n; i++) {
            Vector3f dir = new Vector3f(j[i + 1]).sub(j[i]);
            float len = dir.length();
            if (len < 1.0E-5F) continue;
            TentacleRenderer.frame(ps, j[i], dir.div(len), d.hinge());
            TentacleRenderer.Lit lit = new TentacleRenderer.Lit(light[i], light[i + 1], 0, len / PX);
            float start = i == 0 ? ROOT_HUB * 0.8F : JOINT_HUB[i - 1] * 0.8F;
            float sl = Math.max(Math.min(SLEEVE_MAX, 0.6F * len) / PX, start + 1.2F);
            float sw = SLEEVE[i], cw = COLLAR[i], rw = ROD[i];
            TentacleRenderer.box(ps.last(), vc, sp.sleeve(), lit, space, -sw, sw, start, sl - 0.15F, -sw, sw, TentacleRenderer.Mode.SLEEVE);
            if (detail) TentacleRenderer.box(ps.last(), vc, sp.collar(), lit, space, -cw, cw, sl - 0.9F, sl, -cw, cw, TentacleRenderer.Mode.BAND);
            TentacleRenderer.box(ps.last(), vc, sp.rod(), lit, space, -rw, rw, sl - 0.9F, len / PX, -rw, rw, TentacleRenderer.Mode.TUBE);
            ps.popPose();
            if (i + 2 < n && hubs) joint(ps, vc, sp, j[i + 1], new Vector3f(j[i + 1]).sub(j[i]), new Vector3f(j[i + 2]).sub(j[i + 1]), d.hinge(),
                    JOINT_HUB[i], light[i + 1], detail, space);
        }
        Vector3f w = d.wrist();
        PoseStack.Pose pose = ps.last();
        if (space.firstPerson() && pose.pose().transformPosition(w, new Vector3f()).length() < NEAR_EYE * 2) return false;
        TentacleRenderer.frame(ps, w, d.dir(), d.hinge());
        gripper(ps, vc, sp, light[n - 1], d.open(), detail, space);
        ps.popPose();
        return true;
    }

    /** A joint hub between two segments, its axle and cogs along the hinge. */
    private static void joint(PoseStack ps, VertexConsumer vc, TentacleRenderer.Sprites sp, Vector3f at, Vector3f in, Vector3f out, Vector3f hinge,
                              float r, int light, boolean detail, TentacleRenderer.Space space) {
        Vector3f y = new Vector3f(in).normalize().add(new Vector3f(out).normalize());
        if (y.lengthSquared() < 1.0E-6F) y.set(in);
        TentacleRenderer.frame(ps, at, y.normalize(), hinge);
        TentacleRenderer.Lit lit = TentacleRenderer.Lit.flat(light);
        TentacleRenderer.box(ps.last(), vc, sp.hub(), lit, space, -r, r, -r, r, -r, r, TentacleRenderer.Mode.BLOCK);
        if (detail) {
            TentacleRenderer.box(ps.last(), vc, sp.rod(), lit, space, -r - 0.6F, r + 0.6F, -0.4F, 0.4F, -0.4F, 0.4F, TentacleRenderer.Mode.AXLE);
            TentacleRenderer.decalX(ps.last(), vc, sp.cog(), light, space, r + 0.35F, r * 2.4F);
            TentacleRenderer.decalX(ps.last(), vc, sp.cog(), light, space, -r - 0.35F, r * 2.4F);
        }
        ps.popPose();
    }

    /**
     * The gripper in the wrist frame (+Y along the arm, X the hinge): a wrist hub, a short palm with a collar band and three
     * two-jointed fingers that close to a point at {@code open} 0 and spread at 1.
     */
    private static void gripper(PoseStack ps, VertexConsumer vc, TentacleRenderer.Sprites sp, int light, float open, boolean detail, TentacleRenderer.Space space) {
        TentacleRenderer.Lit lit = TentacleRenderer.Lit.flat(light);
        TentacleRenderer.box(ps.last(), vc, sp.hub(), lit, space, -WRIST_HUB, WRIST_HUB, -WRIST_HUB, WRIST_HUB, -WRIST_HUB, WRIST_HUB, TentacleRenderer.Mode.BLOCK);
        TentacleRenderer.box(ps.last(), vc, sp.sleeve(), lit, space, -PALM, PALM, WRIST_HUB * 0.6F, PALM_LEN + WRIST_HUB * 0.6F, -PALM, PALM, TentacleRenderer.Mode.SLEEVE);
        float top = PALM_LEN + WRIST_HUB * 0.6F;
        if (detail) TentacleRenderer.box(ps.last(), vc, sp.collar(), lit, space, -PALM - 0.2F, PALM + 0.2F, top - 0.7F, top, -PALM - 0.2F, PALM + 0.2F, TentacleRenderer.Mode.BAND);
        float o = Mth.clamp(open, 0, 1);
        float spread = (float) Math.toRadians(-6 + 46 * o), curl = (float) Math.toRadians(30 - 12 * o);
        for (int f = 0; f < 3; f++) {
            double az = Math.toRadians(90 + 120 * f);
            float cx = (float) Math.cos(az), cz = (float) Math.sin(az);
            ps.pushPose();
            ps.translate(cx * HINGE_R * PX, top * PX, cz * HINGE_R * PX);
            ps.mulPose(new Quaternionf().rotationAxis(spread, cz, 0, -cx));
            TentacleRenderer.box(ps.last(), vc, sp.rod(), lit, space, -FINGER_HW, FINGER_HW, -0.3F, PROX, -FINGER_HW, FINGER_HW, TentacleRenderer.Mode.TUBE);
            ps.translate(0, PROX * PX, 0);
            ps.mulPose(new Quaternionf().rotationAxis(-curl, cz, 0, -cx));
            TentacleRenderer.box(ps.last(), vc, sp.collar(), lit, space, -FINGER_HW * 1.15F, FINGER_HW * 1.15F, -0.35F, 0.35F, -FINGER_HW * 1.15F, FINGER_HW * 1.15F, TentacleRenderer.Mode.BAND);
            TentacleRenderer.box(ps.last(), vc, sp.rod(), lit, space, -FINGER_HW * 0.85F, FINGER_HW * 0.85F, 0, DIST, -FINGER_HW * 0.85F, FINGER_HW * 0.85F, TentacleRenderer.Mode.TUBE);
            ps.popPose();
        }
    }

    /**
     * The main-hand item between the gripper's fingers, as a hand holds it: a tool across the gripper with its blade toward the
     * head's up (tipped forward by a strike), a block upright.
     */
    private static void drawItem(PoseStack ps, MultiBufferSource buffers, Player player, Drawn d, TentacleRenderer.Space space) {
        ItemStack stack = player.getMainHandItem();
        Minecraft mc = Minecraft.getInstance();
        Vector3f dir = d.dir();
        float top = PALM_LEN + WRIST_HUB * 0.6F;
        Vector3f grip = new Vector3f(d.wrist()).fma((top + PROX * 0.8F) * PX, dir);
        int light = space.light(player.level(), grip, d.wrist());
        BakedModel model = mc.getItemRenderer().getModel(stack, player.level(), player, player.getId());
        ps.pushPose();
        if (stack.getItem() instanceof BlockItem && model.isGui3d()) {
            Vector3f up = new Vector3f(d.front());
            TentacleRenderer.frame(ps, new Vector3f(grip).fma(0.12F, dir), up, new Vector3f(d.hinge()));
            ps.mulPose(Axis.YP.rotationDegrees(d.side() * 30));
            float k = space.firstPerson() ? 0.7F * HAND_ITEM_SCALE : 0.7F;
            ps.scale(k, k, k);
            mc.getItemRenderer().renderStatic(player, stack, ItemDisplayContext.FIXED, false, ps, buffers, player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
            ps.popPose();
        } else {
            Vector3f front = new Vector3f(d.front()).fma(-d.front().dot(dir), dir);
            if (front.lengthSquared() < 1.0E-6F) front.set(d.hinge()).cross(dir);
            front.normalize();
            TentacleRenderer.frame(ps, grip, dir, new Vector3f(front).cross(dir));
            if (d.tilt() != 0) ps.mulPose(Axis.XP.rotation(-d.tilt()));
            if (space.firstPerson()) ps.scale(HAND_ITEM_SCALE, HAND_ITEM_SCALE, HAND_ITEM_SCALE);
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            ps.mulPose(Axis.YP.rotationDegrees(180));
            mc.getItemRenderer().renderStatic(player, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, ps, buffers, player.level(), light,
                    OverlayTexture.NO_OVERLAY, player.getId());
            ps.popPose();
        }
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
