package dev.tentaworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import com.simibubi.create.content.equipment.zapper.ZapperItem;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.compat.Frame;
import dev.tentaworks.compat.ShipFrames;
import dev.tentaworks.rig.ActuatorReach;
import dev.tentaworks.rig.Actuators;
import dev.tentaworks.rig.FarUse;
import dev.tentaworks.rig.HoldArm;
import dev.tentaworks.rig.RigAir;
import dev.tentaworks.rig.RigSide;
import dev.tentaworks.rig.RigTier;
import dev.tentaworks.rig.TentacleRigItem;
import dev.tentaworks.rig.WornRig;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static dev.tentaworks.client.ActuatorPose.LEFT;
import static dev.tentaworks.client.ActuatorPose.RIGHT;
import static dev.tentaworks.client.TentacleRenderer.PX;

/**
 * The two Actuators of a fitted Rig on every wearer, Stowed or Active, chest or Back Slot, drawn from the Tentacles' own parts
 * and tier textures: a hub on each top corner of the tank, three rigid telescoping segments (sleeve, collar, rod) joined by hubs,
 * and a three-finger gripper. Each Actuator is a hand: the one on the wearer's main-arm side stands in for the main hand, the
 * other for the off hand ({@link #standsIn}). It carries its hand's item, and every swing and use of that hand is its own: a
 * tool strikes the target in step with the vanilla swing, anything else thrusts at it, a held use takes its pose by
 * {@link UseAnim}; with Dual Wield both strike in turn. At rest they curl above the shoulders, breathing; they lean toward a far
 * target under the crosshair, point at a grabbable one, hold a Grab ({@link ActuatorClient#holdArm}), follow a Throw through, and
 * hang limp without Air. In first person they enter from the frame's sides; an arm working near the eye is drawn in the hand
 * pass in place of the vanilla hand, one reaching into the world in the world pass.
 * <p>
 * Inputs change once per tick ({@link #tick}); every pose level chases its goal every frame ({@link #update}), and the own
 * wearer's acting arm follows the crosshair every frame, so nothing waits for the server.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class ActuatorRenderer {
    private static final Int2ObjectMap<Anim> ANIMS = new Int2ObjectOpenHashMap<>();
    private static final float GRAB_OPEN = 0.75F, REACH_OPEN = 0.7F, POINT_OPEN = 0.55F, THROW_OPEN = 1F, LIMP_OPEN = 0.35F, CARRY_OPEN = 0.12F,
            EMPTY_OPEN = 0.5F;
    /** How far an Actuator uncurls toward a grabbable mob, the throw follow-through, how far it uncurls toward a mob it glances at, blocks. */
    private static final double POINT_DISTANCE = 1.1, THROW_DISTANCE = 1.6, GLANCE_DISTANCE = 0.75;
    /** How straight each pose's chain is (wrist distance / chain length). */
    private static final double POINT_STRAIGHT = 0.85, FAR_STRAIGHT = 0.88, HOLD_STRAIGHT = 0.9, HEAVY_STRAIGHT = 0.8, THROW_STRAIGHT = 0.97,
            LIMP_STRAIGHT = 0.88, CARRY_STRAIGHT = 0.8, FOLD_STRAIGHT = 0.5;
    /** Third person: a carried item follows the head's pitch up to this far up or down, degrees (an arm does not reach behind the head). */
    private static final float CARRY_PITCH = 50;
    /** First person: a carried item's size at the eye against the third-person one, its size at the end of a far reach, and the tip distances between. */
    private static final double HAND_ITEM_SCALE = 0.6, FAR_ITEM_SCALE = 1.6, NEAR_TIP = 1.2, FAR_TIP = 6;
    /** First person: an arm whose tip is farther than this from the eye goes to the world pass, and comes back nearer than the second, blocks. */
    private static final double WORLD_PASS_OUT = 1.9, WORLD_PASS_IN = 1.6;
    /** Vertical field of view of the hand pass, degrees. */
    private static final double HAND_FOV = 70;
    /** An arm stays on its target this many ticks after its swing ends; a swing at nothing strikes this far ahead, blocks. */
    private static final int LINGER_TICKS = 4;
    private static final double AIR_DISTANCE = 1.9;
    /** A gripper pulled in short of a block keeps this far from it, blocks. */
    private static final double ROOM = 0.15;
    /** How far an arm leans toward a far target under the crosshair before any action: this much of the way to its ready pose, at most this many blocks. */
    private static final double PRE_AIM = 0.3, PRE_AIM_BLOCKS = 0.4;
    /** A glance looks for a mob within this range, every this many ticks; a reach-out hisses at most once per this many ticks. */
    private static final double GLANCE_RANGE = 6;
    private static final int GLANCE_TICKS = 10, HISS_TICKS = 16;
    /** A glance lasts this long, and the same mob gets another one only after this many ticks. */
    private static final int GLANCE_LENGTH = 45, GLANCE_AGAIN = 400;
    /** Part sizes (px): hubs, sleeve, collar and rod half widths from the shoulder segment to the wrist one, about 2/3 of a Tentacle's. */
    private static final float ROOT_HUB = 1.4F, WRIST_HUB = 0.95F;
    private static final float[] JOINT_HUB = {1.15F, 1.05F}, SLEEVE = {1.0F, 0.85F, 0.72F}, COLLAR = {1.3F, 1.12F, 0.95F}, ROD = {0.66F, 0.56F, 0.48F};
    /** Longest sleeve (blocks); past it a segment grows by a middle tube sliding out, up to its own longest, then by the rod. */
    private static final float SLEEVE_MAX = 0.42F, TUBE_MAX = 0.7F;
    /** Gripper (px): palm half width and length, finger hinge radius, half width, first and second joint lengths. */
    private static final float PALM = 1.15F, PALM_LEN = 1.7F, HINGE_R = 0.75F, FINGER_HW = 0.4F, PROX = 2.4F, DIST = 1.9F;
    /** First person: parts this close to the eye are not drawn, blocks. */
    private static final float NEAR_EYE = 0.25F;
    private static final InteractionHand[] HANDS = InteractionHand.values();
    private static float worldM11 = (float) (1 / Math.tan(Math.toRadians(HAND_FOV) / 2));
    /** Own wearer: the hand's last click was a use (a swing after it is a thrust, not a strike). */
    private static final boolean[] USED = new boolean[2];
    private static long frame, handFrame = -1;
    /** First-person draws of an Actuator standing in for a hand, per hand (main, off): the harness checks no hand is drawn twice in a frame. */
    public static final int[] HAND_DRAWS = new int[2];

    /** Which Actuators a draw call is for: all of them, or in first person those of the world pass or of the hand pass. */
    enum Pass {
        ALL, WORLD, HAND
    }

    /** One Actuator's animation. Inputs are set once per tick, levels (0..1) and the target chase them every frame. */
    static final class Arm {
        final int side;
        boolean main, hand, carries, empty, acting, attack, strike, dual, loop, using, pointing, holding, world;
        Item item;
        UseAnim useAnim = UseAnim.NONE;
        float used;
        double lean;
        int swingStart = Integer.MIN_VALUE / 2, lastSwing = Integer.MIN_VALUE / 2, equipTick = Integer.MIN_VALUE / 2;
        Vec3 goal, to, at, pointAt, lastHold, farFrom;
        /** Normal of the block face the arm works on (null: a mob or the air), and the one the tool follows this frame. */
        Vec3 face, n;
        Vec3[] probe;
        double carry, bare, act, use, point, hold, glance, tipDistance;
        /** Turn of the chain's bend about its root-to-wrist line that keeps it out of blocks: this frame's and the goal, picked once a tick. */
        double bend, bendGoal;
        /** How much of the way from the shoulder to its aimed grip the arm goes, short of a block in between: this frame's and the goal. */
        double room = 1, roomGoal = 1;
        int bendTick = Integer.MIN_VALUE / 2;

        Arm(int side) {
            this.side = side;
        }

        void clear() {
            hand = carries = empty = acting = attack = strike = dual = loop = using = pointing = holding = world = false;
            lean = carry = bare = act = use = point = hold = glance = 0;
            goal = to = at = pointAt = lastHold = farFrom = face = n = null;
            probe = null;
            bend = bendGoal = 0;
            room = roomGoal = 1;
        }
    }

    /** One player's Actuators. */
    static final class Anim {
        boolean fitted, limp, rightHanded = true, heavy;
        int flexStart = Integer.MIN_VALUE / 2, grabStart = Integer.MIN_VALUE / 2, throwStart = Integer.MIN_VALUE / 2, hissTick = Integer.MIN_VALUE / 2;
        final Arm[] arms = {new Arm(LEFT), new Arm(RIGHT)};
        final boolean[] throwArm = new boolean[2];
        Vec3 throwDir = Vec3.ZERO;
        HoldArm hold = HoldArm.NONE;
        int heldId = -1, glanceId = -1, glanceStart = Integer.MIN_VALUE / 2;
        BlockPos ship;
        Direction shipFace = Direction.UP;
        double limpK, lowAir;
        float time = Float.NaN;

        Anim(boolean fitted) {
            this.fitted = fitted;
        }

        Arm arm(boolean mainHand) {
            return arms[ActuatorPose.side(rightHanded, mainHand) < 0 ? 0 : 1];
        }

        Arm arm(InteractionHand hand) {
            return arm(hand == InteractionHand.MAIN_HAND);
        }
    }

    /**
     * What an Actuator aims for: the spot its fingers meet on, the axis and edge of the item there, the way the gripper holding
     * it points ({@code palm}), the way its chain bulges, how straight, how open the gripper, how much the gripper turns from
     * its arm's line to the palm ({@code held}), and the item's size.
     */
    record Aim(Vec3 grip, Vec3 axis, Vec3 edge, Vec3 palm, Vec3 bulge, double straight, float open, double held, double scale) {
        Aim blend(Aim o, double k) {
            if (k <= 0) return this;
            if (k >= 1) return o;
            return new Aim(grip.lerp(o.grip, k), axis.lerp(o.axis, k), edge.lerp(o.edge, k), palm.lerp(o.palm, k), bulge.lerp(o.bulge, k),
                    Mth.lerp(k, straight, o.straight), (float) Mth.lerp(k, open, o.open), Mth.lerp(k, held, o.held), Mth.lerp(k, scale, o.scale));
        }

        Aim withOpen(float open) {
            return new Aim(grip, axis, edge, palm, bulge, straight, open, held, scale);
        }

        Aim withGrip(Vec3 grip) {
            return new Aim(grip, axis, edge, palm, bulge, straight, open, held, scale);
        }

        Aim withAxis(Vec3 to) {
            if (to == axis) return this;
            Vec3 square = palm.subtract(to.scale(palm.dot(to)));
            return new Aim(grip, to, edge, square.lengthSqr() < 1.0E-6 ? palm : square.normalize(), bulge, straight, open, held, scale);
        }
    }

    /** An Actuator in tank space, ready to draw: joints root to wrist, hinge axis, gripper opening and direction, the item's axis, edge and size. */
    private record Drawn(Vector3f[] j, Vector3f hinge, float open, int side, Vector3f dir, Vector3f axis, Vector3f edge, float scale) {
        Vector3f wrist() {
            return j[j.length - 1];
        }
    }

    private ActuatorRenderer() {
    }

    static Anim anim(Player player) {
        return ANIMS.get(player.getId());
    }

    /**
     * The hand's Actuator stands in for it, so the vanilla hand, arm and item of that hand are not drawn and the body's arm
     * stays still: Actuators ready (fitted, with Air), no Extendo Grip in either hand (Create lifts the reach itself), and not an
     * item Create or vanilla draws in first person in its own way (Potato Cannon, Worldshaper, a map).
     */
    public static boolean standsIn(LivingEntity entity, InteractionHand hand) {
        Anim a = entity instanceof Player ? ANIMS.get(entity.getId()) : null;
        return a != null && a.fitted && a.arm(hand).hand;
    }

    /** The hand's Actuator carries its item: it {@link #standsIn stands in} for the hand, the hand is not empty and the Actuator holds no Grab. */
    public static boolean carries(LivingEntity entity, InteractionHand hand) {
        Anim a = entity instanceof Player ? ANIMS.get(entity.getId()) : null;
        return a != null && a.fitted && a.arm(hand).carries;
    }

    /** The swing of the player's swinging hand is its Actuator's: the body's arm does not swing. */
    public static boolean swings(LivingEntity entity) {
        return entity instanceof Player && standsIn(entity, entity.swingingArm == null ? InteractionHand.MAIN_HAND : entity.swingingArm);
    }

    /** How far the player's acting main-hand Actuator still is from the spot it works on, blocks; -1 while it does not act (the harness reads it). */
    public static double lag(Player player) {
        Anim a = ANIMS.get(player.getId());
        Arm arm = a == null ? null : a.arm(true);
        return arm == null || !arm.acting || arm.at == null || arm.to == null ? -1 : arm.at.distanceTo(arm.to);
    }

    private static boolean excluded(ItemStack stack) {
        return stack.getItem() instanceof MapItem || stack.getItem() instanceof PotatoCannonItem || stack.getItem() instanceof ZapperItem;
    }

    /** A tool or weapon strikes; anything else, and an empty gripper, thrusts. */
    private static boolean striker(ItemStack stack) {
        return stack.getItem() instanceof TieredItem || stack.getItem() instanceof TridentItem || stack.getItem() instanceof MaceItem;
    }

    /** A Throw by {@code player} along {@code dir}: the holding arms snap out along it and come back slowly. */
    static void thrown(Player player, Vec3 dir) {
        Anim a = ANIMS.get(player.getId());
        if (a == null) return;
        a.throwStart = player.tickCount;
        a.throwDir = dir.lengthSqr() < 1.0E-6 ? player.getViewVector(1.0F) : dir.normalize();
        HoldArm hold = a.hold.holding() ? a.hold : HoldArm.OFF;
        for (InteractionHand h : HANDS) a.throwArm[a.arm(h).side < 0 ? 0 : 1] = hold.uses(h);
    }

    /** Culling: a wearer whose body is off screen still renders while an Actuator reaches or holds in view. */
    public static boolean inView(Player player, Frustum frustum) {
        Anim a = ANIMS.get(player.getId());
        if (a == null || !a.fitted) return false;
        AABB box = player.getBoundingBox().inflate(0.8, 0.6, 0.8);
        for (Arm arm : a.arms) {
            if (arm.act > 0.05 && arm.at != null) box = box.minmax(new AABB(arm.at, arm.at));
            if (arm.holding && arm.lastHold != null) box = box.minmax(new AABB(arm.lastHold, arm.lastHold));
        }
        if (a.hold.holding()) {
            Entity held = a.heldId >= 0 ? player.level().getEntity(a.heldId) : null;
            if (held != null) box = box.minmax(held.getBoundingBox());
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

    /** First person: vanilla draws its hands this frame (not with the GUI hidden, asleep or as a spectator), and the wearer is not scoping. */
    private static boolean handPass(Player player) {
        Minecraft mc = Minecraft.getInstance();
        return player == mc.player && mc.getCameraEntity() == player && mc.options.getCameraType().isFirstPerson() && !mc.options.hideGui
                && !player.isScoping() && !player.isSleeping() && !player.isSpectator();
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
    static void onFrame(RenderFrameEvent.Pre event) {
        frame++;
    }

    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack()) USED[0] = false;
        else if (event.isUseItem()) USED[event.getHand() == InteractionHand.MAIN_HAND ? 0 : 1] = true;
    }

    private static void tick(Minecraft mc, AbstractClientPlayer p, Anim a) {
        boolean fitted = Actuators.fitted(p);
        if (fitted && !a.fitted) a.flexStart = p.tickCount;
        a.fitted = fitted;
        if (!fitted) {
            for (Arm arm : a.arms) arm.clear();
            a.hold = HoldArm.NONE;
            a.heldId = a.glanceId = -1;
            a.ship = null;
            a.limpK = 0;
            return;
        }
        boolean own = p == mc.player, ready = Actuators.ready(p), extendo = FarUse.extendo(p);
        a.limp = !ready;
        a.rightHanded = p.getMainArm() == HumanoidArm.RIGHT;
        ActuatorClient.State s = ActuatorClient.get(p);
        HoldArm hold = ready && s.holding() ? s.arm() : HoldArm.NONE;
        if (hold.holding() && !a.hold.holding()) a.grabStart = p.tickCount;
        a.hold = hold;
        a.heavy = hold == HoldArm.BOTH;
        a.heldId = hold.holding() ? s.heldId() : -1;
        a.ship = hold.holding() ? s.ship() : null;
        a.shipFace = s.shipFace();
        a.lowAir = lowAir(p);
        if ((p.tickCount + p.getId()) % GLANCE_TICKS == 0) {
            int id = glance(mc, p, a);
            if (id >= 0 && (id != a.glanceId || p.tickCount - a.glanceStart > GLANCE_AGAIN)) a.glanceStart = p.tickCount;
            a.glanceId = id;
        }
        HitResult hit = own ? mc.hitResult : null;
        boolean mining = own && mc.screen == null && mc.options.keyAttack.isDown() && hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK;
        Vec3 far = s.farTarget();
        for (InteractionHand h : HANDS) {
            boolean main = h == InteractionHand.MAIN_HAND;
            Arm arm = a.arm(main);
            ItemStack stack = p.getItemInHand(h);
            arm.main = main;
            arm.hand = ready && !extendo && !excluded(stack);
            arm.holding = hold.uses(h);
            arm.carries = arm.hand && !stack.isEmpty() && !arm.holding;
            arm.empty = own && main && arm.hand && stack.isEmpty() && !arm.holding;
            if (stack.getItem() != arm.item) {
                if (arm.item != null) arm.equipTick = p.tickCount;
                arm.item = stack.getItem();
            }
            boolean swinging = p.swinging && p.swingingArm == h;
            if (swinging && p.swingTime <= 0) {
                arm.loop = p.tickCount - arm.swingStart < ActuatorPose.SWING_TICKS;
                arm.swingStart = p.tickCount;
                arm.attack = !(own && USED[main ? 0 : 1]);
                arm.strike = arm.attack && striker(stack);
                if (own) ActuatorSight.fired(mc.player);
            }
            if (swinging) arm.lastSwing = p.tickCount;
            else arm.loop = false;
            arm.using = arm.carries && p.isUsingItem() && p.getUsedItemHand() == h && p.getUseItem().getUseAnimation() != UseAnim.NONE;
            if (arm.using) {
                arm.useAnim = p.getUseItem().getUseAnimation();
                arm.used = p.getTicksUsingItem();
            }
            boolean was = arm.acting;
            arm.acting = arm.hand && !arm.holding && !arm.using
                    && (swinging || main && mining || p.tickCount - arm.lastSwing <= LINGER_TICKS);
            arm.dual = false;
            if (own && arm.acting && hit != null && hit.getType() != HitResult.Type.MISS) {
                arm.goal = hit.getLocation();
                arm.face = face(hit);
            }
            if (!own && arm.acting) {
                if (far != null) {
                    if (!far.equals(arm.farFrom)) {
                        arm.farFrom = far;
                        BlockHitResult on = surface(p, far);
                        arm.goal = on == null ? far : on.getLocation();
                        arm.face = face(on);
                    }
                } else if (!was || swinging && p.swingTime <= 0) {
                    HitResult on = pick(p);
                    arm.goal = on.getLocation();
                    arm.face = face(on);
                    arm.farFrom = null;
                }
            }
            if (arm.acting && !was && arm.goal != null && arm.goal.distanceToSqr(p.getEyePosition()) > 9 && p.tickCount - a.hissTick >= HISS_TICKS) {
                a.hissTick = p.tickCount;
                RigSounds.hiss(p, 0.05F, 1.7F);
            }
        }
        Arm mainArm = a.arm(true), offArm = a.arm(false);
        if (mainArm.acting && mainArm.attack && offArm.hand && offArm.carries && !offArm.using
                && ActuatorClient.dualWield(p, own ? hit : pick(p))) {
            offArm.dual = true;
            offArm.acting = true;
            offArm.goal = mainArm.goal;
            offArm.face = mainArm.face;
        }
        Vec3 cueAt = own ? ActuatorSight.cuePoint() : null, pre = own ? ActuatorSight.farPoint() : null;
        boolean dualMark = own && ActuatorSight.mark() == ActuatorSight.Mark.DUAL;
        for (InteractionHand h : HANDS) {
            Arm arm = a.arm(h);
            arm.pointing = cueAt != null && ActuatorSight.grabs(h) && arm.hand && !arm.acting && !arm.using;
            if (arm.pointing) arm.pointAt = cueAt;
            arm.lean = pre != null && arm.hand && !arm.holding && !arm.using && (arm.main || arm.carries && (dualMark || mainArm.holding))
                    ? Math.min(PRE_AIM, PRE_AIM_BLOCKS / Math.max(0.5, pre.distanceTo(p.getEyePosition()))) : 0;
            if (own && !arm.acting && arm.lean > 0) {
                arm.goal = pre;
                arm.face = face(hit);
            }
        }
    }

    /** What another player's hand works on: the first block or mob along its look within its Reach, else a point in the air ahead. */
    private static HitResult pick(Player p) {
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1.0F);
        double reach = Math.max(3, ActuatorReach.pickRange(TentacleRigItem.reach(p)));
        BlockHitResult block = p.level().clip(new ClipContext(eye, eye.add(look.scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        double range = block.getType() == HitResult.Type.MISS ? reach : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(range));
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0),
                e -> !e.isSpectator() && e.isPickable(), range * range);
        if (entity != null) return entity;
        return block.getType() == HitResult.Type.MISS ? BlockHitResult.miss(eye.add(look.scale(AIR_DISTANCE)), Direction.UP, block.getBlockPos()) : block;
    }

    /**
     * How much of the way from the shoulder {@code s} to the aimed grip (or the tip of the item it carries) an arm can go: all of it, or up to a block in between less
     * a little room for the gripper (a Rest Pose under a low ceiling, a carried item against a wall). A shoulder inside a block
     * leaves it alone.
     */
    private static double room(Level level, Vec3 s, Vec3 grip) {
        double len = grip.distanceTo(s);
        if (len < 1.0E-3 || solid(level, s)) return 1;
        BlockHitResult hit = level.clip(new ClipContext(s, grip, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return 1;
        return Mth.clamp((hit.getLocation().distanceTo(s) - ROOM) / len, 0.15, 1);
    }

    /** A carried item's axis tilted so its far end stays off the block face it would reach into ({@link ActuatorClearance#tilt}). */
    private static Vec3 itemClear(Level level, Vec3 grip, Vec3 axis, double len) {
        if (axis.lengthSqr() < 1.0E-8 || solid(level, grip)) return axis;
        Vec3 a = axis.normalize();
        BlockHitResult hit = level.clip(new ClipContext(grip, grip.add(a.scale(len + ActuatorClearance.ITEM_CLEAR * 2)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return axis;
        Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 t = ActuatorClearance.tilt(a, n, grip.subtract(hit.getLocation()).dot(n), len);
        return t == a ? axis : t;
    }

    /** The normal of the block face a pick lands on, or null (a mob, the air). */
    private static Vec3 face(HitResult hit) {
        return hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? Vec3.atLowerCornerOf(b.getDirection().getNormal()) : null;
    }

    /** Another player's far target as a face: the server sends a block's centre; the face of it the wearer's eye sees, or null. */
    private static BlockHitResult surface(Player p, Vec3 target) {
        Vec3 eye = p.getEyePosition(), d = target.subtract(eye);
        double len = d.length();
        if (len < 1.0E-6) return null;
        BlockHitResult hit = p.level().clip(new ClipContext(eye, target.add(d.scale(0.05 / len)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** A point on its way to a new target, moved out of a block it cuts through along the target face's normal (at most a block). */
    private static Vec3 outside(Level level, Vec3 p, Vec3 n) {
        if (n == null || !solid(level, p)) return p;
        for (int i = 1; i <= 5; i++) {
            Vec3 q = p.add(n.scale(0.2 * i));
            if (!solid(level, q)) return q;
        }
        return p;
    }

    /** A world point inside a block's collision shape. */
    static boolean solid(Level level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        if (state.isCollisionShapeFullBlock(level, pos)) return true;
        Vec3 local = p.subtract(pos.getX(), pos.getY(), pos.getZ());
        for (AABB box : shape.toAabbs()) if (box.contains(local)) return true;
        return false;
    }

    /** The mob an idle Actuator glances at: the nearest living one within {@link #GLANCE_RANGE} (wearers near the camera only), or -1. */
    private static int glance(Minecraft mc, Player p, Anim a) {
        if (a.limp || p.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 32 * 32) return -1;
        Entity best = null;
        double bestD = GLANCE_RANGE * GLANCE_RANGE;
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(GLANCE_RANGE), e -> e.isAlive() && e.getId() != a.heldId)) {
            double d = m.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best == null ? -1 : best.getId();
    }

    private static double lowAir(Player p) {
        if (RigAir.free(p)) return 0;
        ItemStack rig = RigAir.worn(p);
        int max = rig.isEmpty() ? 0 : BacktankUtil.maxAir(rig);
        return max <= 0 ? 0 : ActuatorPose.lowAir(RigAir.air(p) / (double) max);
    }

    private static double chase(double value, double goal, double dt, double tau, boolean snap) {
        if (snap) return goal;
        double next = value + (goal - value) * ActuatorPose.chase(dt, tau);
        return Math.abs(goal - next) < 0.002 ? goal : next;
    }

    /** Every frame: the levels chase their goals, the acting arm's target follows the crosshair (own wearer) or the last known target. */
    private static void update(Minecraft mc, Player p, Anim a, Body b, float time) {
        double dt = time - a.time;
        if (dt == 0) return;
        boolean snap = !(dt > 0 && dt < 10);
        a.time = time;
        boolean own = p == mc.player;
        a.limpK = chase(a.limpK, a.limp ? 1 : 0, dt, ActuatorPose.IDLE_TAU, snap);
        Entity glance = a.glanceId >= 0 ? p.level().getEntity(a.glanceId) : null;
        for (Arm arm : a.arms) {
            arm.carry = chase(arm.carry, arm.carries || arm.empty && b.first ? 1 : 0, dt, ActuatorPose.POSE_TAU, snap);
            arm.bare = chase(arm.bare, arm.carries ? 0 : 1, dt, ActuatorPose.POSE_TAU, snap);
            arm.use = chase(arm.use, arm.using ? 1 : 0, dt, ActuatorPose.POSE_TAU, snap);
            arm.point = chase(arm.point, arm.pointing ? 1 : 0, dt, ActuatorPose.POSE_TAU, snap);
            arm.hold = chase(arm.hold, arm.holding ? 1 : 0, dt, ActuatorPose.POSE_TAU, snap);
            boolean idle = !arm.carries && !arm.acting && !arm.holding && !arm.pointing;
            boolean looks = glance != null && idle && time - a.glanceStart < GLANCE_LENGTH && glance.position().subtract(p.position()).dot(b.out(arm.side)) > 0;
            arm.glance = chase(arm.glance, looks ? 1 : 0, dt, ActuatorPose.IDLE_TAU, snap);
            double goal = arm.acting ? 1 : arm.lean;
            boolean fresh = arm.act < 0.02;
            arm.act = chase(arm.act, goal, dt, goal > arm.act ? ActuatorPose.ACT_TAU : ActuatorPose.BACK_TAU, snap);
            arm.bend = snap ? arm.bendGoal : ActuatorClearance.turnToward(arm.bend, arm.bendGoal, ActuatorPose.chase(dt, ActuatorPose.AIM_TAU));
            arm.room = chase(arm.room, arm.roomGoal, dt, ActuatorPose.AIM_TAU, snap);
            Vec3 to = arm.goal;
            if (own && arm.acting) to = contact(mc, a, arm, b);
            else if (to != null) to = offset(to, a, arm, b);
            Vec3 face = arm.face != null ? arm.face : b.look.scale(-1);
            arm.n = snap || fresh ? face : ActuatorClearance.turnNormal(arm.n, face, ActuatorPose.chase(dt, ActuatorPose.AIM_TAU));
            arm.to = to;
            if (to != null && (arm.acting || arm.lean > 0))
                arm.at = arm.at == null || snap || fresh ? to : outside(b.level, arm.at.lerp(to, ActuatorPose.chase(dt, ActuatorPose.AIM_TAU)), arm.n);
            if (b.first) arm.world = arm.tipDistance > (arm.world ? WORLD_PASS_IN : WORLD_PASS_OUT);
            else arm.world = false;
        }
    }

    /** Own wearer: where the acting arm's item meets the target this frame: the crosshair's pick, or a point in the air ahead. */
    private static Vec3 contact(Minecraft mc, Anim a, Arm arm, Body b) {
        HitResult hit = mc.hitResult;
        Vec3 at = hit != null && hit.getType() != HitResult.Type.MISS ? hit.getLocation() : b.eye.add(b.look.scale(AIR_DISTANCE));
        arm.goal = at;
        arm.face = face(hit);
        return offset(at, a, arm, b);
    }

    /**
     * The contact point sits just off the surface, so the item does not sink into it: on a block face off it along its normal,
     * else toward the eye. First person: also a little to the arm's own side of the crosshair and below it (along the face), so
     * the item does not cover what the wearer looks at; with both arms on one target they land side by side.
     */
    private static Vec3 offset(Vec3 at, Anim a, Arm arm, Body b) {
        double d = at.distanceTo(b.eye);
        boolean pair = arm.dual || a.arm(!arm.main).dual;
        Vec3 nudge;
        if (!b.first) {
            Vec3 side = pair ? b.out(arm.side).scale(0.14) : Vec3.ZERO;
            nudge = arm.face != null || d < 1.0E-6 ? side : side.subtract(at.subtract(b.eye).scale(Math.min(0.25, d * 0.2) / d));
        } else {
            double out = pair ? 0.07 : 0.035;
            nudge = b.headRight.scale(arm.side * out * d).subtract(b.headUp.scale(0.02 * d)).subtract(b.look.scale(Math.min(0.1, d * 0.2)));
        }
        if (arm.face == null) return at.add(nudge);
        Vec3 on = ActuatorClearance.onFace(at, nudge, arm.face);
        return b.level.isLoaded(BlockPos.containing(on)) && solid(b.level, on) ? ActuatorClearance.onFace(at, Vec3.ZERO, arm.face) : on;
    }

    /**
     * First person, once per frame at the first hand vanilla is about to draw: the Actuators that work near the eye, in the hand
     * pass (no wall clips them, F1 hides them like a hand). The hand pass has its own field of view; the world-to-camera
     * transform is scaled so an arm lands on screen exactly where the world pass would draw it. A hand its Actuator stands in
     * for is not drawn by vanilla at all.
     */
    @SubscribeEvent(priority = EventPriority.HIGH, receiveCanceled = true)
    static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.getCameraEntity() != player) return;
        if (handFrame != frame) {
            handFrame = frame;
            if (handPass(player) && Actuators.fitted(player) && WornRig.of(player).item() instanceof TentacleRigItem rig) {
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
                TentacleRenderer.timed(() -> draw(ps, event.getMultiBufferSource(), player, rig.tier(), pt, space, Pass.HAND));
                ps.popPose();
            }
        }
        if (standsIn(player, event.getHand())) event.setCanceled(true);
    }

    /** Draws a fitted Rig's Actuators of {@code pass}, and the items they carry. */
    static void draw(PoseStack ps, MultiBufferSource buffers, Player player, RigTier tier, float pt, TentacleRenderer.Space space, Pass pass) {
        if (!Actuators.fitted(player)) return;
        TentacleRenderer.Sprites sp = TentacleRenderer.Sprites.of(TentacleRenderer.Style.of(tier));
        float time = player.tickCount + pt;
        Anim a = ANIMS.get(player.getId());
        Drawn[] arms = new Drawn[2];
        boolean hands = space.firstPerson() && handPass(player);
        if (!space.world() || a == null) {
            for (int i = 0; i < 2; i++) arms[i] = restInTank(i == 0 ? LEFT : RIGHT, time);
        } else {
            Matrix4f toTank = new Matrix4f(space.toCamera()).invert();
            Body b = Body.of(player, space, pt);
            update(Minecraft.getInstance(), player, a, b, time);
            for (int i = 0; i < 2; i++) {
                Arm arm = a.arms[i];
                boolean inHand = hands && !arm.world;
                if (pass == Pass.HAND && !inHand || pass == Pass.WORLD && inHand) continue;
                Vec3 s = b.shoulder(arm.side);
                Aim aim = aim(arm, a, player, b, space, time, pt);
                arm.tipDistance = aim.grip().distanceTo(b.eye);
                boolean clear = !inHand || arm.act > 0.05;
                ItemStack stack = arm.carries ? player.getItemInHand(arm.main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND) : ItemStack.EMPTY;
                if (!inHand) {
                    if (arm.bendTick != player.tickCount) arm.roomGoal = room(player.level(), s, aim.grip());
                    if (arm.room < 1) aim = aim.withGrip(s.add(aim.grip().subtract(s).scale(arm.room)));
                    if (!arm.acting && !stack.isEmpty()) aim = aim.withAxis(itemClear(player.level(), aim.grip(), aim.axis(), itemLength(stack, aim)));
                } else {
                    arm.roomGoal = arm.room = 1;
                }
                ActuatorPose.Hand hand = ActuatorPose.hand(s, aim.grip(), aim.palm(), aim.held());
                double seg = ActuatorPose.segment(s.distanceTo(hand.wrist()), aim.straight(), b.maxSegment);
                Vec3 fallback = b.out(arm.side).add(b.up.scale(0.3));
                if (clear) {
                    Level level = player.level();
                    if (arm.bendTick != player.tickCount
                            || ActuatorClearance.inside(ActuatorClearance.chain(s, hand.wrist(), aim.bulge(), fallback, seg, arm.bend), q -> solid(level, q)) > 0) {
                        arm.bendTick = player.tickCount;
                        arm.bendGoal = ActuatorClearance.bend(s, hand.wrist(), aim.bulge(), fallback, seg, arm.bendGoal, q -> solid(level, q));
                    }
                } else {
                    arm.bendGoal = 0;
                }
                ActuatorPose.Chain chain = ActuatorClearance.chain(s, hand.wrist(), aim.bulge(), fallback, seg, arm.bend);
                Vec3 d = handDir(chain, aim);
                arm.probe = probe(chain, d, aim, stack);
                arms[i] = toTank(toTank, space, chain, aim, d, arm.side);
            }
        }
        boolean[] tip = new boolean[2];
        VertexConsumer vc = LimbBuffer.get(buffers, space.firstPerson());
        for (int i = 0; i < 2; i++) if (arms[i] != null) tip[i] = drawArm(ps, vc, sp, arms[i], player.level(), space);
        if (a == null || space.firstPerson() && !hands) return;
        for (int i = 0; i < 2; i++) {
            if (!tip[i]) continue;
            Arm arm = a.arms[i];
            InteractionHand h = arm.main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            if (space.firstPerson() && arm.hand) HAND_DRAWS[arm.main ? 0 : 1]++;
            if (arm.carries && !player.getItemInHand(h).isEmpty()) drawItem(ps, buffers, player, player.getItemInHand(h), arms[i], space);
        }
    }

    /** The wearer's frame this frame (world): shoulders, the body's out (per side) and up, the head's look basis and the longest segment. */
    private record Body(Vec3 left, Vec3 right, Vec3 rightDir, Vec3 up, Vec3 forward, Vec3 eye, Vec3 look, Vec3 headRight, Vec3 headUp,
                        boolean first, double handScale, double maxSegment, Level level) {
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
                        eye, look, headRight, headUp, false, 1, maxSegment, player.level());
            }
            Minecraft mc = Minecraft.getInstance();
            Camera cam = mc.gameRenderer.getMainCamera();
            Vec3 camLook = vec(cam.getLookVector()), camUp = vec(cam.getUpVector()), camRight = vec(cam.getLeftVector()).scale(-1);
            double fov = mc.options.fov().get();
            double aspect = mc.getWindow().getHeight() == 0 ? 16 / 9.0 : (double) mc.getWindow().getWidth() / mc.getWindow().getHeight();
            Vec3 c = space.camera();
            return new Body(entry(c, ActuatorPose.entry(LEFT, fov, aspect), camRight, camUp, camLook),
                    entry(c, ActuatorPose.entry(RIGHT, fov, aspect), camRight, camUp, camLook), camRight, camUp, camLook,
                    c, camLook, camRight, camUp, true, ActuatorRenderer.handScale(), maxSegment, player.level());
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

        /** A view-space spot of an Actuator on {@code side} (x toward its own side, y up, z ahead) in the world. */
        Vec3 view(int side, Vec3 p, Vec3 look, Vec3 up) {
            return eye.add(look.scale(p.z)).add(headRight.scale(side * p.x / handScale)).add(up.scale(p.y / handScale));
        }
    }

    /**
     * This frame's aim of one Actuator: Rest (with the Fitting flex and a glance), then carrying, acting, pointing, hold, throw,
     * flinch, brace and limp blended over it.
     */
    private static Aim aim(Arm arm, Anim a, Player player, Body b, TentacleRenderer.Space space, float time, float pt) {
        int side = arm.side;
        Vec3 s = b.shoulder(side);
        Aim pose = rest(arm, a, player, b, space, time);
        float flexAge = time - a.flexStart;
        double fold = ActuatorPose.folded(flexAge);
        if (fold > 0) pose = pose.blend(folded(side, s, b, space), fold);
        double flex = ActuatorPose.flexOpen(flexAge);
        if (flex > 0) pose = pose.withOpen((float) Mth.lerp(flex, pose.open(), 0.95F));
        if (arm.carry > 0) pose = pose.blend(carry(arm, a, player, b, time, pt), arm.carry);
        if (arm.act > 0 && arm.at != null) pose = pose.blend(act(arm, a, player, b, s, time, pt), arm.act);
        if (arm.point > 0 && arm.pointAt != null)
            pose = pose.blend(toward(s, arm.pointAt, Math.min(POINT_DISTANCE, arm.pointAt.distanceTo(s) * 0.5), b, side, POINT_STRAIGHT, POINT_OPEN), arm.point);
        if (arm.hold > 0) {
            Vec3 tip = holdPoint(arm, a, player, b, s, pt);
            if (tip != null) arm.lastHold = tip;
            else tip = arm.lastHold;
            if (tip != null) {
                double wind = ActuatorPose.windUp(time - a.grabStart);
                if (wind > 0) tip = tip.lerp(s.add(b.up.scale(0.35)).add(b.out(side).scale(0.25)).subtract(b.forward.scale(0.15)), wind);
                double regrip = ActuatorPose.bump(time, 64, 7, player.getId() + side);
                Vec3 bulge = a.heavy ? b.up.add(b.out(side).scale(0.4)) : b.up.scale(0.6).add(b.out(side));
                pose = pose.blend(free(s, tip, bulge, a.heavy ? HEAVY_STRAIGHT : HOLD_STRAIGHT, (float) (GRAB_OPEN + 0.22 * regrip), b), arm.hold);
            }
        }
        if (a.throwArm[side < 0 ? 0 : 1]) {
            double k = ActuatorPose.followThrough(time - a.throwStart);
            if (k > 0) pose = pose.blend(toward(s, s.add(a.throwDir), THROW_DISTANCE, b, side, THROW_STRAIGHT, THROW_OPEN), k);
        }
        if (arm.hold < 0.5) {
            double flinch = ActuatorPose.flinch(player.hurtTime - pt, player.hurtDuration);
            if (flinch > 0) {
                Vec3 guard = b.eye.add(b.look.scale(0.45)).add(b.headRight.scale(side * 0.3 / b.handScale)).subtract(b.headUp.scale(0.25 / b.handScale));
                pose = pose.blend(free(s, guard, b.up.add(b.out(side)), 0.75, 0.6F, b), flinch * (arm.carry > 0.5 ? 0.3 : 0.7));
            }
            double brace = player.onGround() || player.isFallFlying() || player.isInWater() || player.getAbilities().flying || RigSide.carried(player) ? 0
                    : ActuatorPose.brace(-player.getDeltaMovement().y);
            if (brace > 0) {
                Vec3 wide = s.add(b.out(side).scale(0.75)).add(b.forward.scale(0.2)).subtract(b.up.scale(0.05));
                pose = pose.blend(free(s, wide, b.up, 0.9, 0.9F, b), brace * (arm.carry > 0.5 ? 0.25 : 0.85));
            }
        }
        if (a.limpK > 0) pose = pose.blend(limpPose(side, s, b, space), a.limpK);
        return pose;
    }

    /** A pose with nothing in the gripper: the wrist on {@code wrist}, the gripper in line with the arm. */
    private static Aim free(Vec3 s, Vec3 wrist, Vec3 bulge, double straight, float open, Body b) {
        Vec3 d = wrist.subtract(s);
        double len = d.length();
        Vec3 grip = len < 1.0E-6 ? wrist : wrist.add(d.scale(ActuatorPose.GRIP_LENGTH / len));
        return new Aim(grip, b.headUp, b.look, d.lengthSqr() < 1.0E-8 ? b.look : d, bulge, straight, open, 0, b.first ? HAND_ITEM_SCALE : 1);
    }

    /** A free pose whose fingers meet on {@code tip}. */
    private static Aim reach(Vec3 s, Vec3 tip, Vec3 bulge, double straight, float open, Body b) {
        Vec3 d = tip.subtract(s);
        double len = d.length();
        return free(s, len < 1.0E-6 ? tip : s.add(d.scale(Math.max(0.05, len - ActuatorPose.GRIP_LENGTH) / len)), bulge, straight, open, b);
    }

    private static Aim toward(Vec3 s, Vec3 target, double distance, Body b, int side, double straight, float open) {
        Vec3 d = target.subtract(s);
        if (d.lengthSqr() < 1.0E-8) d = b.forward;
        return reach(s, s.add(d.normalize().scale(Math.max(0.2, distance))), b.up.scale(0.5).add(b.out(side)), straight, open, b);
    }

    /**
     * An Actuator carrying its hand's item: held where a hand would hold it (first person: low on its own side of the view), in
     * the pose of a held use while the item is used; it dips and regrips when the item changes, weighs a tool now and then, and
     * shivers when the Air runs low. The own wearer's empty main hand waits there as an open gripper.
     */
    private static Aim carry(Arm arm, Anim a, Player player, Body b, float time, float pt) {
        boolean first = b.first;
        int side = arm.side;
        Vec3 look = b.look, up = b.headUp;
        if (!first) {
            look = Vec3.directionFromRotation(Mth.clamp(player.getViewXRot(pt), -CARRY_PITCH, CARRY_PITCH), player.getViewYRot(pt));
            up = b.headRight.cross(look).normalize();
        }
        ActuatorPose.Grip g = ActuatorPose.carry(first);
        Vec3 at = g.at();
        double tilt = g.tilt(), along = g.along();
        if (arm.bare > 0 && first) at = at.lerp(ActuatorPose.BARE, arm.bare);
        if (arm.use > 0) {
            ActuatorPose.Grip u = ActuatorPose.use(arm.useAnim, first, arm.using ? player.getTicksUsingItem() + pt : arm.used);
            at = at.lerp(u.at(), arm.use);
            tilt = Mth.lerp(arm.use, tilt, u.tilt());
            along = Mth.lerp(arm.use, along, u.along());
        } else if (arm.carries && arm.act < 0.05) {
            tilt += 0.4 * ActuatorPose.bump(time, 230, 18, player.getId() * 7 + side);
        }
        float equip = time - arm.equipTick;
        double dip = equip >= 0 && equip < 5 ? Math.sin(equip / 5 * Math.PI) : 0;
        double shiver = a.lowAir > 0 ? 0.012 * a.lowAir * Math.sin(time * 2.9 + side) : 0;
        Vec3 grip = b.view(side, at.add(0, shiver - 0.14 * dip - 0.05 * a.lowAir, 0), look, up);
        Vec3 axis = up.scale(Math.cos(tilt)).add(look.scale(Math.sin(tilt)));
        Vec3 edge = look.scale(Math.cos(tilt)).subtract(up.scale(Math.sin(tilt)));
        Vec3 bulge = first ? b.out(side).add(b.up.scale(0.25)) : b.up.add(b.out(side).scale(0.7));
        float open = (float) (Mth.lerp(arm.bare, CARRY_OPEN, EMPTY_OPEN) + 0.45 * dip);
        return new Aim(grip, axis, edge, ActuatorPose.palm(b.shoulder(side), grip, axis, look, along), bulge, CARRY_STRAIGHT, open, 1, first ? HAND_ITEM_SCALE : 1);
    }

    /**
     * An Actuator at work on its target: a tool strikes it ({@link ActuatorPose#strikeAt}: its head lands on the target at the
     * impact of the vanilla swing), anything else and an empty gripper thrusts at it. Between swings it waits ready at the target.
     */
    private static Aim act(Arm arm, Anim a, Player player, Body b, Vec3 s, float time, float pt) {
        int side = arm.side;
        Vec3 target = arm.at;
        Vec3 d = target.subtract(s);
        double len = d.length();
        Vec3 dir = len < 1.0E-6 ? b.look : d.scale(1 / len);
        double p = swing(arm, a, player, time, pt);
        double scale = b.first ? Mth.lerp(ActuatorPose.smooth((target.distanceTo(b.eye) - NEAR_TIP) / (FAR_TIP - NEAR_TIP)), HAND_ITEM_SCALE, FAR_ITEM_SCALE) : 1;
        Vec3 bulge = b.first ? b.out(side).add(b.up.scale(0.25)) : b.up.scale(0.5).add(b.out(side));
        ItemStack stack = player.getItemInHand(arm.main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (arm.carries && (arm.strike || arm.dual) && striker(stack)) {
            ActuatorPose.Swing plane;
            if (b.first) {
                plane = ActuatorPose.Swing.of(b.look.scale(0.5).subtract(b.headRight.scale(side * 0.62)).add(b.headUp.scale(0.6)),
                        b.headUp.scale(0.7).add(b.headRight.scale(side * 0.7)).subtract(b.look.scale(0.25)));
            } else {
                Vec3 flat = new Vec3(b.look.x, 0, b.look.z);
                flat = flat.lengthSqr() < 1.0E-6 ? b.forward : flat.normalize();
                Vec3 w = new Vec3(0, 1, 0).subtract(flat.scale(0.6));
                Vec3 over = w.subtract(dir.scale(w.dot(dir)));
                over = over.lengthSqr() < 1.0E-6 ? b.up : over.normalize();
                plane = ActuatorPose.Swing.of(dir.scale(0.78).subtract(over.scale(0.62)), over.scale(0.8).subtract(dir.scale(0.6)));
            }
            if (arm.n != null) plane = ActuatorClearance.onFace(plane, arm.n, ActuatorClearance.MIN_INTO);
            Vec3 pull = dir.scale(-0.22 * scale).add(plane.back().scale(0.1 * scale));
            ActuatorPose.Tool t = ActuatorPose.strikeAt(target, plane, ActuatorPose.TOOL_LENGTH * scale, p, pull, b.first ? -0.85 * side : 0);
            return new Aim(t.grip(), t.axis(), t.edge(), t.palm(), bulge, FAR_STRAIGHT, CARRY_OPEN, 1, scale);
        }
        double k = ActuatorPose.thrust(p);
        if (arm.n != null) dir = ActuatorClearance.into(dir, arm.n, ActuatorClearance.MIN_INTO);
        if (!arm.carries) {
            Vec3 tip = target.subtract(dir.scale(Mth.lerp(k, 0.45, 0.03)));
            return reach(s, tip, bulge, FAR_STRAIGHT, (float) Mth.lerp(k, REACH_OPEN, 0.15), b);
        }
        double stand = (stack.getItem() instanceof BlockItem ? 0.34 : 0.24) * scale;
        Vec3 grip = target.subtract(dir.scale(stand + 0.45 * (1 - k)));
        Vec3 axis = b.headUp.subtract(dir.scale(b.headUp.dot(dir)));
        axis = axis.lengthSqr() < 1.0E-6 ? b.up : axis.normalize();
        return new Aim(grip, axis, dir, dir, bulge, FAR_STRAIGHT, CARRY_OPEN, 1, scale);
    }

    /**
     * The arm's swing progress, 0..1 (0 between swings): its hand's vanilla swing; a Dual Wield off-hand arm strikes half a
     * mining cycle after the main one, or a beat after a single blow.
     */
    private static double swing(Arm arm, Anim a, Player player, float time, float pt) {
        if (!arm.dual) {
            InteractionHand h = arm.main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            return player.swinging && player.swingingArm == h ? player.getAttackAnim(pt) : 0;
        }
        Arm m = a.arm(true);
        Minecraft mc = Minecraft.getInstance();
        boolean swinging = player.swinging && player.swingingArm == InteractionHand.MAIN_HAND;
        boolean loop = m.loop || player == mc.player && mc.options.keyAttack.isDown() && mc.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK;
        if (swinging && loop) {
            double mp = player.getAttackAnim(pt);
            return ActuatorPose.dualMining(mp < 0.5 ? mp * ActuatorPose.SWING_TICKS : ActuatorPose.MINING_CYCLE - 1 + (mp - 0.5) * 2);
        }
        return Math.max(0, ActuatorPose.dualMelee(time - m.swingStart - 1));
    }

    /** Where an Actuator takes hold this frame: the near side of a light target, either flank of a heavy one, the hull face of a Ship. */
    private static Vec3 holdPoint(Arm arm, Anim a, Player player, Body b, Vec3 s, float pt) {
        Level level = player.level();
        int side = arm.side;
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

    /**
     * Rest Pose, alive: the tips drift after the head's look, droop and shiver when the Air runs low, and the arm on a nearby
     * mob's side uncurls a little toward it.
     */
    private static Aim rest(Arm arm, Anim a, Player player, Body b, TentacleRenderer.Space space, float time) {
        int side = arm.side;
        Vec3 s = b.shoulder(side);
        Vec3 wrist = world(space, ActuatorPose.restWrist(side, time));
        if (!b.first) wrist = wrist.add(b.look.subtract(b.forward).scale(0.12));
        if (a.lowAir > 0) wrist = wrist.subtract(b.up.scale(a.lowAir * (0.14 + 0.012 * Math.sin(time * 2.9 + side))));
        Aim pose = free(s, wrist, vec(space.direction(vec3f(ActuatorPose.restBulge(side)))), ActuatorPose.restStraight(time),
                ActuatorPose.restOpen(side, time), b);
        if (arm.glance > 0) {
            Entity mob = a.glanceId >= 0 ? player.level().getEntity(a.glanceId) : null;
            if (mob != null) pose = pose.blend(toward(s, mob.getEyePosition(), GLANCE_DISTANCE, b, side, POINT_STRAIGHT, 0.4F), 0.45 * arm.glance);
        }
        return pose;
    }

    private static Aim folded(int side, Vec3 s, Body b, TentacleRenderer.Space space) {
        Vec3 sh = ActuatorPose.shoulder(side);
        return free(s, world(space, sh.add(side * 0.06, 0.18, 0.06)), vec(space.direction(new Vector3f(side, 0.4F, 0.5F))), FOLD_STRAIGHT, 0, b);
    }

    private static Aim limpPose(int side, Vec3 s, Body b, TentacleRenderer.Space space) {
        Vec3 sh = ActuatorPose.shoulder(side);
        return free(s, world(space, sh.add(side * 0.3, -0.62, -0.02)), vec(space.direction(new Vector3f(side, 0.25F, 0.35F))), LIMP_STRAIGHT, LIMP_OPEN, b);
    }

    /** Rest Pose straight in tank space, for the inventory preview (no world). */
    private static Drawn restInTank(int side, float time) {
        Vec3 s = ActuatorPose.shoulder(side), w = ActuatorPose.restWrist(side, time);
        double seg = ActuatorPose.segment(s.distanceTo(w), ActuatorPose.restStraight(time), 4);
        ActuatorPose.Chain chain = ActuatorPose.chain(s, w, ActuatorPose.restBulge(side), new Vec3(side, 0.3, 0), seg);
        Vector3f[] j = new Vector3f[chain.joints().length];
        for (int i = 0; i < j.length; i++) j[i] = vec3f(chain.joints()[i]);
        Vector3f dir = vec3f(chain.wristDir());
        Vector3f axis = new Vector3f(0, 1, 0).fma(-dir.y, dir);
        if (axis.lengthSquared() < 1.0E-6F) axis.set(0, 0, -1);
        return new Drawn(j, vec3f(chain.hinge()), ActuatorPose.restOpen(side, time), side, dir, axis.normalize(), new Vector3f(dir), 1);
    }

    /** The way the drawn gripper points (world): along the last segment, turned toward the aimed grip by how much the gripper holds. */
    private static Vec3 handDir(ActuatorPose.Chain chain, Aim aim) {
        Vec3 line = chain.wristDir(), to = aim.grip().subtract(chain.wrist());
        Vec3 d = aim.held() <= 0 || to.lengthSqr() < 1.0E-8 ? line : line.lerp(to.normalize(), Math.min(1, aim.held()));
        return d.lengthSqr() < 1.0E-8 ? line : d.normalize();
    }

    /** The drawn item's axis (world, unit): the aimed one, squared to the gripper as far as it does not hold. */
    private static Vec3 itemAxis(ActuatorPose.Chain chain, Aim aim, Vec3 d) {
        Vec3 ax = aim.axis().subtract(d.scale(aim.axis().dot(d) * (1 - Math.min(1, aim.held()))));
        if (ax.lengthSqr() < 1.0E-8) ax = chain.hinge().cross(d);
        return ax.normalize();
    }

    /** World points of a drawn Actuator past its root: along each segment, the gripper and the item it carries. */
    private static Vec3[] probe(ActuatorPose.Chain chain, Vec3 d, Aim aim, ItemStack stack) {
        Vec3[] j = chain.joints();
        int n = j.length - 1;
        boolean item = !stack.isEmpty();
        Vec3[] out = new Vec3[n * 3 + 1 + (item ? 2 : 0)];
        int k = 0;
        for (int i = 0; i < n; i++) for (int t = 1; t <= 3; t++) out[k++] = j[i].lerp(j[i + 1], t / 3.0);
        Vec3 grip = chain.wrist().add(d.scale(ActuatorPose.GRIP_LENGTH));
        out[k++] = grip;
        if (item) {
            Vec3 ax = itemAxis(chain, aim, d);
            double len = itemLength(stack, aim);
            out[k++] = grip.add(ax.scale(len * 0.5));
            out[k] = grip.add(ax.scale(len));
        }
        return out;
    }

    private static double itemLength(ItemStack stack, Aim aim) {
        return (striker(stack) ? ActuatorPose.TOOL_LENGTH : 0.3) * aim.scale();
    }

    /** The own wearer's drawn points of one Actuator this frame (world), empty when not drawn (the harness reads it). */
    public static Vec3[] probe(Player player, boolean main) {
        Anim a = ANIMS.get(player.getId());
        Vec3[] p = a == null || !a.fitted ? null : a.arm(main).probe;
        return p == null ? new Vec3[0] : p;
    }

    private static Drawn toTank(Matrix4f toTank, TentacleRenderer.Space space, ActuatorPose.Chain chain, Aim aim, Vec3 d, int side) {
        Vec3 c = space.camera();
        Vec3[] w = chain.joints();
        Vector3f[] j = new Vector3f[w.length];
        for (int i = 0; i < w.length; i++) {
            j[i] = toTank.transformPosition((float) (w[i].x - c.x), (float) (w[i].y - c.y), (float) (w[i].z - c.z), new Vector3f());
            if (!j[i].isFinite()) return null;
        }
        Vec3 ax = itemAxis(chain, aim, d);
        Vec3 ed = aim.edge().subtract(ax.scale(aim.edge().dot(ax)));
        if (ed.lengthSqr() < 1.0E-8) ed = d.subtract(ax.scale(d.dot(ax)));
        if (ed.lengthSqr() < 1.0E-8) ed = chain.hinge().cross(ax);
        Vector3f hinge = toTank.transformDirection(vec3f(chain.hinge()), new Vector3f());
        Vector3f dir = toTank.transformDirection(vec3f(d), new Vector3f());
        Vector3f axis = toTank.transformDirection(vec3f(ax), new Vector3f());
        Vector3f edge = toTank.transformDirection(vec3f(ed.normalize()), new Vector3f());
        if (!hinge.isFinite() || hinge.lengthSquared() < 1.0E-8F || !dir.isFinite() || dir.lengthSquared() < 1.0E-8F || !axis.isFinite()
                || axis.lengthSquared() < 1.0E-8F || !edge.isFinite() || edge.lengthSquared() < 1.0E-8F) return null;
        return new Drawn(j, hinge.normalize(), aim.open(), side, dir.normalize(), axis.normalize(), edge.normalize(), (float) aim.scale());
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
            float free = len - sl * PX, from = sl;
            if (free > 0) {
                float mw = (sw + rw) / 2;
                from = sl + Math.min(TUBE_MAX, free * 0.5F) / PX;
                TentacleRenderer.box(ps.last(), vc, sp.rod(), lit, space, -mw, mw, sl - 0.9F, from - 0.15F, -mw, mw, TentacleRenderer.Mode.TUBE);
                if (detail) TentacleRenderer.box(ps.last(), vc, sp.collar(), lit, space, -mw - 0.22F, mw + 0.22F, from - 0.8F, from, -mw - 0.22F, mw + 0.22F, TentacleRenderer.Mode.BAND);
            }
            TentacleRenderer.box(ps.last(), vc, sp.rod(), lit, space, -rw, rw, from - 0.9F, len / PX, -rw, rw, TentacleRenderer.Mode.TUBE);
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
     * A hand's item between the gripper's fingers, as a hand holds it: a tool along its axis with its edge leading, a block
     * upright on the gripper.
     */
    private static void drawItem(PoseStack ps, MultiBufferSource buffers, Player player, ItemStack stack, Drawn d, TentacleRenderer.Space space) {
        Minecraft mc = Minecraft.getInstance();
        Vector3f dir = d.dir();
        float top = PALM_LEN + WRIST_HUB * 0.6F;
        Vector3f grip = new Vector3f(d.wrist()).fma((top + PROX * 0.8F) * PX, dir);
        int light = space.light(player.level(), grip, d.wrist());
        if (light == 0) light = LevelRenderer.getLightColor(player.level(), BlockPos.containing(player.getEyePosition()));
        BakedModel model = mc.getItemRenderer().getModel(stack, player.level(), player, player.getId());
        float k = d.scale();
        if (stack.getItem() instanceof BlockItem && model.isGui3d()) {
            TentacleRenderer.frame(ps, new Vector3f(grip).fma(0.12F, dir), d.axis(), new Vector3f(d.hinge()));
            ps.mulPose(Axis.YP.rotationDegrees(d.side() * 30));
            ps.scale(0.7F * k, 0.7F * k, 0.7F * k);
            mc.getItemRenderer().renderStatic(player, stack, ItemDisplayContext.FIXED, false, ps, buffers, player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
        } else {
            TentacleRenderer.frame(ps, grip, d.edge(), new Vector3f(d.axis()).cross(d.edge()));
            ps.scale(k, k, k);
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            ps.mulPose(Axis.YP.rotationDegrees(180));
            mc.getItemRenderer().renderStatic(player, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, ps, buffers, player.level(), light,
                    OverlayTexture.NO_OVERLAY, player.getId());
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
