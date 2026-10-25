package dev.tentaworks.mixin;

import dev.tentaworks.client.RigVisuals;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A moving carried wearer's body turns with the head, smoothly. Vanilla turns it toward the direction of travel (which a Rig
 * move wobbles tick to tick), flips it for backward travel and snaps it to the head while swinging an arm: the shoulders,
 * and the four Tentacles hanging off them, jittered and crossed over the back. Standing still keeps vanilla's rule (the body
 * stays until the head turns past 50°), so looking around while Held does not move the gait's targets. Drawing only, on every client.
 */
@Mixin(LivingEntity.class)
public class LivingEntityBodyYawMixin {
    private static final float FOLLOW = 0.25F, MAX_PER_TICK = 15.0F, MAX_HEAD_OFFSET = 50.0F;
    /** Vanilla's "moving" threshold for turning the body: squared horizontal move per tick. */
    private static final double MOVING = 0.0025;

    @Shadow public float yBodyRot;

    @Inject(method = "tickHeadTurn", at = @At("HEAD"), cancellable = true)
    private void tentaworks$bodyFollowsHeadWhileCarried(float yRot, float animStep, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof Player player) || !player.level().isClientSide()) return;
        RigVisuals.Visual v = RigVisuals.get(player);
        if (v == null || !v.state().carries()) return;
        float head = player.getYRot();
        double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
        if (dx * dx + dz * dz > MOVING) {
            float d = Mth.wrapDegrees(head - yBodyRot);
            yBodyRot += Mth.clamp(d * FOLLOW, -MAX_PER_TICK, MAX_PER_TICK);
        }
        float left = Mth.wrapDegrees(head - yBodyRot);
        if (Math.abs(left) > MAX_HEAD_OFFSET) yBodyRot += left - Mth.sign(left) * MAX_HEAD_OFFSET;
        cir.setReturnValue(animStep);
    }
}
