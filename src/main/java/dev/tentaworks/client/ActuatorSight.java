package dev.tentaworks.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.Actuators;
import dev.tentaworks.rig.FarUse;
import dev.tentaworks.rig.RigSettings;
import dev.tentaworks.rig.RigSide;
import dev.tentaworks.rig.WornRig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
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
import org.jetbrains.annotations.Nullable;

/**
 * What the wearer's crosshair rests on, as the Actuators see it, once per tick: the mark the crosshair shows ({@link Mark}), the
 * Grab cue, and the point a far action would go to (the pre-aim leans toward it).
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class ActuatorSight {
    /** A target this far past Reach still shows as just out of Reach, blocks. */
    public static final double OUT_PAST = 3;
    private static Mark mark = Mark.NONE;
    private static ActuatorView.Cue cue = ActuatorView.Cue.NONE;
    private static @Nullable Vec3 point;
    private static @Nullable Vec3 cuePoint;
    private static boolean far;
    private static int fired = Integer.MIN_VALUE / 2, cueSince;

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

    public static ActuatorView.Cue cue() {
        return cue;
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
        return cue.grab() ? cuePoint : null;
    }

    /** An action of the wearer just fired: the marks close in for a moment. */
    static void fired(LocalPlayer player) {
        fired = player.tickCount;
    }

    /** Ticks since the last action fired. */
    static float firedAge(LocalPlayer player, float pt) {
        return player.tickCount - fired + pt;
    }

    /**
     * The mark for a crosshair target. {@code allowed}: Far Use may act now but for the Air (mode, no Extendo Grip, no heavy
     * hold); {@code beyondArm}: the target is past arm's length; {@code inReach}: an Actuator reaches it; {@code past}: how far
     * past Reach it is from the eye, blocks (0 or less within); {@code dual}: Dual Wield applies to it.
     */
    public static Mark mark(boolean fitted, boolean limp, boolean allowed, boolean target, boolean beyondArm, boolean inReach, double past,
                            boolean dual) {
        if (!fitted || !target) return Mark.NONE;
        if (dual && !limp) return Mark.DUAL;
        if (!allowed || !beyondArm) return Mark.NONE;
        if (!inReach) return past <= OUT_PAST ? Mark.OUT : Mark.NONE;
        return limp ? Mark.LIMP : Mark.REACH;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ActuatorView.Cue cueWas = cue;
        mark = Mark.NONE;
        cue = ActuatorView.Cue.NONE;
        far = false;
        if (player == null || mc.level == null || player.isSpectator() || !Actuators.fitted(player)) return;
        double reach = ActuatorView.reach(player);
        HitResult vanilla = mc.hitResult;
        boolean picked = vanilla != null && vanilla.getType() != HitResult.Type.MISS;
        HitResult probe = ActuatorClient.aim(player, reach + OUT_PAST);
        HitResult hit = picked ? vanilla : probe;
        boolean target = hit.getType() != HitResult.Type.MISS;
        if (probe.getType() != HitResult.Type.MISS) {
            cue = ActuatorView.grabCue(player, probe);
            cuePoint = probe instanceof EntityHitResult e ? e.getEntity().getBoundingBox().getCenter() : probe.getLocation();
        }
        if (cue != cueWas) cueSince = player.tickCount;
        if (!target) return;
        Vec3 eye = player.getEyePosition();
        boolean entity = hit instanceof EntityHitResult;
        double base = entity ? base(player, Attributes.ENTITY_INTERACTION_RANGE, FarUse.ENTITY_ID)
                : base(player, Attributes.BLOCK_INTERACTION_RANGE, FarUse.REACH_ID);
        double dist = hit.getLocation().distanceTo(eye);
        boolean limp = Actuators.limp(player);
        ActuatorView.HoldArm hold = ActuatorView.holdArm(player);
        boolean allowed = FarUse.lifts(true, RigSettings.of(WornRig.of(player).stack()).farUse(), RigSide.deployed(player), FarUse.extendo(player),
                hold == ActuatorView.HoldArm.BOTH, false);
        boolean inReach = ActuatorView.inReach(player, hit.getLocation());
        mark = mark(true, limp, allowed, true, dist > base, inReach, dist - reach, picked && ActuatorView.dualWield(player, hit));
        point = hit.getLocation();
        far = (mark == Mark.REACH || mark == Mark.DUAL) && dist > base;
    }

    /** An interaction range of the wearer without the Far Use lift: arm's length. */
    static double base(LocalPlayer player, Holder<Attribute> attribute, ResourceLocation lift) {
        AttributeInstance a = player.getAttribute(attribute);
        if (a == null) return 0;
        AttributeModifier m = a.getModifier(lift);
        return a.getValue() - (m == null ? 0 : m.amount());
    }
}
