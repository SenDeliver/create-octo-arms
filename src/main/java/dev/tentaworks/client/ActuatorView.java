package dev.tentaworks.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.compat.ShipFrames;
import dev.tentaworks.rig.Actuators;
import dev.tentaworks.rig.GrabRule;
import dev.tentaworks.rig.TentacleRigItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the Actuators' looks (poses, crosshair marks, hint row, set-down outline) read of the Actuator rules and state,
 * one method per input, so the drawing code never asks the rules itself.
 */
public final class ActuatorView {
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, TentaworksIds.id(TentaworksIds.BOSSES));
    /** A set-down spot shown in place of the real one (dev scripts). */
    private static @Nullable Spot preview;

    /** Which Actuators hold the Grab. */
    public enum HoldArm {
        NONE, MAIN, OFF, BOTH;

        public boolean main() {
            return this == MAIN || this == BOTH;
        }

        public boolean off() {
            return this == OFF || this == BOTH;
        }

        public boolean with(boolean mainHand) {
            return mainHand ? main() : off();
        }
    }

    /** What the Grab key would do to the target under the crosshair. */
    public enum Cue {
        NONE, GRAB_MAIN, GRAB_OFF, GRAB_BOTH, TWO_HANDS, HANDS_FULL, REFUSED;

        public boolean grab() {
            return this == GRAB_MAIN || this == GRAB_OFF || this == GRAB_BOTH;
        }

        public boolean with(boolean mainHand) {
            return this == GRAB_BOTH || this == (mainHand ? GRAB_MAIN : GRAB_OFF);
        }
    }

    /** Where a Set Down would put the held target (the feet of its box), and whether it may go there. */
    public record Spot(Vec3 pos, boolean valid) {
    }

    private ActuatorView() {
    }

    /** Shows {@code spot} as the set-down spot whatever is held (dev scripts); null ends it. */
    public static void preview(@Nullable Spot spot) {
        preview = spot;
    }

    /** Which of the player's Actuators hold its Grab. */
    public static HoldArm holdArm(Player player) {
        ActuatorClient.State s = ActuatorClient.get(player);
        if (!s.holding()) return HoldArm.NONE;
        return s.heavy() || s.ship() != null ? HoldArm.BOTH : HoldArm.OFF;
    }

    /** The mob the player's Actuators hold, or null (nothing, a Ship, or not known here). */
    public static @Nullable Entity held(Player player) {
        ActuatorClient.State s = ActuatorClient.get(player);
        return s.holding() && s.heldId() >= 0 ? player.level().getEntity(s.heldId()) : null;
    }

    /** How far from the eye the held target is kept, blocks; 0 while nothing is held or it is not known. */
    public static double holdDistance(Player player) {
        Entity held = held(player);
        return held == null ? 0 : held.getBoundingBox().getCenter().distanceTo(player.getEyePosition());
    }

    /** A Throw needs the Grab key held with the attack: the main hand holds an item and its Actuator is not the one holding. */
    public static boolean throwNeedsGrabKey(Player player) {
        return !holdArm(player).main() && !player.getMainHandItem().isEmpty();
    }

    /** Where a tap of the Grab key would set the held target down now; null when there is no spot (it is set down where it hangs). */
    public static @Nullable Spot setDownSpot(LocalPlayer player) {
        return preview;
    }

    /** What the Grab key would do to {@code target}, the crosshair's pick within Reach. */
    public static Cue grabCue(LocalPlayer player, @Nullable HitResult target) {
        if (target == null || !Actuators.ready(player) || holdArm(player) != HoldArm.NONE) return Cue.NONE;
        boolean main = player.getMainHandItem().isEmpty(), off = player.getOffhandItem().isEmpty();
        if (target instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
            if (!ShipFrames.get().isPlot(player.level(), b.getBlockPos()) || !inReach(player, b.getLocation())) return Cue.NONE;
            return main && off ? Cue.GRAB_BOTH : Cue.TWO_HANDS;
        }
        if (!(target instanceof EntityHitResult e) || !(e.getEntity() instanceof LivingEntity t) || t instanceof Player) return Cue.NONE;
        if (!inReach(player, t.getBoundingBox().getCenter())) return Cue.NONE;
        if (t.isPassenger() || t.isVehicle() || t.getType().is(BOSSES) || t.getType().is(Tags.EntityTypes.BOSSES)
                || t.getMaxHealth() > GrabRule.BOSS_HEALTH) return Cue.REFUSED;
        if (!GrabRule.light(t.getBbWidth(), t.getBbHeight())) return main && off ? Cue.GRAB_BOTH : Cue.TWO_HANDS;
        return off ? Cue.GRAB_OFF : main ? Cue.GRAB_MAIN : Cue.HANDS_FULL;
    }

    /** The Grab key is held down now. */
    public static boolean grabKeyDown() {
        return TentaworksKeys.GRAB.isDown();
    }

    /** Dual Wield applies to an attack on {@code target}: both Actuators would work on it. */
    public static boolean dualWield(Player player, @Nullable HitResult target) {
        if (target == null || !Actuators.ready(player) || holdArm(player) != HoldArm.NONE) return false;
        ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
        if (main.isEmpty() || off.isEmpty()) return false;
        if (target instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
            BlockState state = player.level().getBlockState(b.getBlockPos());
            return effective(main, state) && effective(off, state);
        }
        return target instanceof EntityHitResult && weapon(main) && weapon(off);
    }

    /** The player's Reach, blocks. */
    public static double reach(Player player) {
        return TentacleRigItem.reach(player);
    }

    /** A point the player's Actuators can reach. */
    public static boolean inReach(Player player, Vec3 point) {
        double reach = reach(player);
        return point.distanceToSqr(player.getEyePosition()) <= reach * reach;
    }

    private static boolean effective(ItemStack tool, BlockState state) {
        return tool.isCorrectToolForDrops(state) || tool.getDestroySpeed(state) > 1;
    }

    private static boolean weapon(ItemStack stack) {
        return stack.getAttributeModifiers().modifiers().stream().anyMatch(e -> e.attribute().is(Attributes.ATTACK_DAMAGE));
    }
}
