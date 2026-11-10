package dev.tentaworks.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.client.ActuatorClient.Affordance;
import dev.tentaworks.rig.ActuatorReach;
import dev.tentaworks.rig.Actuators;
import dev.tentaworks.rig.FarUse;
import dev.tentaworks.rig.GrabRule;
import dev.tentaworks.rig.Grips;
import dev.tentaworks.rig.HoldArm;
import dev.tentaworks.rig.RigSettings;
import dev.tentaworks.rig.RigSide;
import dev.tentaworks.rig.TentacleRigItem;
import dev.tentaworks.rig.WornRig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What the wearer's crosshair rests on, as the Actuators see it, once per tick: the mark the crosshair shows ({@link Mark}), the
 * Grab cue (the Grab key's {@link ActuatorClient#affordance}), and the point a far action would go to (the pre-aim leans toward
 * it). Reach is the Actuators' own rule: a point within Reach of the Rig body ({@link ActuatorReach}), as the crosshair pick and
 * the server use it.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class ActuatorSight {
    /** A target this far past Reach still shows as just out of Reach, blocks. */
    public static final double OUT_PAST = 1.5;
    /** A click on nothing flashes the out-of-Reach marks when a target lies this far past Reach or nearer, blocks. */
    public static final double MISS_PAST = 4;
    private static Mark mark = Mark.NONE;
    private static Affordance cue = Affordance.NONE;
    private static @Nullable Vec3 point;
    private static boolean far, beyond;
    private static int fired = Integer.MIN_VALUE / 2, missed = Integer.MIN_VALUE / 2, cueSince;

    /** What the crosshair marks say. */
    public enum Mark {
        /** Nothing an Actuator would be needed for. */
        NONE,
        /** Past arm's length, within Reach: the Actuator will do it. */
        REACH,
        /** Dual Wield applies: both Actuators will work on it. */
        DUAL,
        /** Just out of Reach. */
        OUT,
        /** In Reach, but the Actuators hang limp. */
        LIMP
    }

    private ActuatorSight() {
    }

    public static Mark mark() {
        return mark;
    }

    /** What the Grab key would do on the crosshair target, as of this tick. */
    public static Affordance cue() {
        return cue;
    }

    /** {@code hand}'s Actuator would take the target under the crosshair. */
    public static boolean grabs(InteractionHand hand) {
        return cue.kind() == Affordance.Kind.GRAB && cue.arm().uses(hand);
    }

    /** Client ticks the current cue has been showing. */
    static int cueAge(LocalPlayer player) {
        return player.tickCount - cueSince;
    }

    /** The far target under the crosshair an action would go to (past arm's length, in Reach), or null. */
    public static @Nullable Vec3 farPoint() {
        return far ? point : null;
    }

    /** The centre of the grabbable target under the crosshair, or null. */
    public static @Nullable Vec3 cuePoint() {
        return cue.kind() == Affordance.Kind.GRAB ? ActuatorClient.affordanceAt() : null;
    }

    /** A swing of the wearer started: the marks close in on the crosshair for its strike. */
    static void fired(LocalPlayer player) {
        fired = player.tickCount;
    }

    /** Ticks since the last swing started. */
    static float firedAge(LocalPlayer player, float pt) {
        return player.tickCount - fired + pt;
    }

    /** A click or a Grab key press went to a target out of Reach: the hollow marks flash. */
    static void missed(LocalPlayer player) {
        missed = player.tickCount;
    }

    /** Ticks since a click or a Grab key press went to a target out of Reach. */
    static float missedAge(LocalPlayer player, float pt) {
        return player.tickCount - missed + pt;
    }

    /**
     * The mark for a crosshair target. {@code allowed}: Far Use may act now but for the Air (mode, no Extendo Grip, no heavy
     * hold); {@code beyondArm}: the target is past arm's length; {@code inReach}: an Actuator reaches it; {@code past}: how far
     * past Reach of the body it is, blocks (0 or less within); {@code dual}: Dual Wield applies to it.
     */
    public static Mark mark(boolean fitted, boolean limp, boolean allowed, boolean target, boolean beyondArm, boolean inReach, double past,
                            boolean dual) {
        if (!fitted || !target) return Mark.NONE;
        if (dual && !limp) return Mark.DUAL;
        if (!allowed || !beyondArm) return Mark.NONE;
        if (!inReach) return past <= OUT_PAST ? Mark.OUT : Mark.NONE;
        return limp ? Mark.LIMP : Mark.REACH;
    }

    /** The cue for an affordance: a player or a thing that is no mob gets none (they are never Grabbed, no need to say so at every look). */
    static Affordance shown(Affordance a) {
        return a.kind() == Affordance.Kind.REFUSED && (a.verdict() == GrabRule.Verdict.PLAYER || a.verdict() == GrabRule.Verdict.NOT_LIVING)
                ? Affordance.NONE : a;
    }

    /** A click that landed on nothing flashes the out-of-Reach marks: the Actuators could act, and a target lies within {@link #MISS_PAST} past Reach. */
    public static boolean misses(boolean allowed, boolean picked, boolean target, double past) {
        return allowed && !picked && target && past > 0 && past <= MISS_PAST;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Affordance cueWas = cue;
        mark = Mark.NONE;
        cue = Affordance.NONE;
        far = beyond = false;
        if (player == null || mc.level == null || player.isSpectator() || !Actuators.fitted(player)) return;
        cue = shown(ActuatorClient.affordance());
        if (cue.kind() != cueWas.kind() || cue.arm() != cueWas.arm()) cueSince = player.tickCount;
        double reach = TentacleRigItem.reach(player);
        HitResult vanilla = mc.hitResult;
        boolean picked = vanilla != null && vanilla.getType() != HitResult.Type.MISS;
        HitResult hit = picked ? vanilla : ActuatorClient.aim(player, ActuatorReach.pickRange(reach) + MISS_PAST);
        if (hit.getType() == HitResult.Type.MISS) return;
        Vec3 eye = player.getEyePosition();
        boolean entity = hit instanceof EntityHitResult;
        double base = entity ? base(player, Attributes.ENTITY_INTERACTION_RANGE, FarUse.ENTITY_ID)
                : base(player, Attributes.BLOCK_INTERACTION_RANGE, FarUse.REACH_ID);
        double dist = hit.getLocation().distanceTo(eye);
        boolean limp = Actuators.limp(player);
        boolean allowed = FarUse.lifts(true, RigSettings.of(WornRig.of(player).stack()).farUse(), RigSide.deployed(player), FarUse.extendo(player),
                ActuatorClient.holdArm(player) == HoldArm.BOTH, false);
        boolean inReach = picked || ActuatorClient.reaches(player, hit.getLocation());
        double past = hit.getLocation().distanceTo(Grips.body(player)) - reach;
        mark = mark(true, limp, allowed, true, dist > base, inReach, past, picked && ActuatorClient.dualWield(player, hit));
        beyond = misses(allowed, picked, true, past);
        point = hit.getLocation();
        far = (mark == Mark.REACH || mark == Mark.DUAL) && dist > base;
    }

    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && beyond && !ActuatorClient.get(player).holding() && (event.isAttack() || event.isUseItem())) missed(player);
    }

    /** An interaction range of the wearer without the Far Use lift: arm's length. */
    static double base(LocalPlayer player, Holder<Attribute> attribute, ResourceLocation lift) {
        AttributeInstance a = player.getAttribute(attribute);
        if (a == null) return 0;
        AttributeModifier m = a.getModifier(lift);
        return a.getValue() - (m == null ? 0 : m.amount());
    }
}
