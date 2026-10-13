package dev.tentaworks.mixin;

import dev.tentaworks.client.RigVisuals;
import dev.tentaworks.config.TentaworksClientConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A carried wearer does not walk in the air: vanilla feeds the walk animation from the position delta, which a Rig move or a
 * Ship carry also produces. While the Rig carries, the animation is fed zero instead and fades out over a few ticks
 * (client config {@code animation.walkWhileHeld}). Works on the wearer's own client and on the clients that watch them.
 */
@Mixin(LivingEntity.class)
public class LivingEntityWalkMixin {
    @Shadow @Final public WalkAnimationState walkAnimation;

    @Inject(method = "updateWalkAnimation", at = @At("HEAD"), cancellable = true)
    private void tentaworks$stillWhileHeld(float partialTick, CallbackInfo ci) {
        if (!((Object) this instanceof Player player) || TentaworksClientConfig.walkWhileHeld()) return;
        RigVisuals.Visual v = RigVisuals.get(player);
        if (v == null || !v.state().carries()) return;
        walkAnimation.update(0.0F, 0.4F);
        ci.cancel();
    }
}
