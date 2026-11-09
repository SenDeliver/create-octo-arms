package dev.tentaworks.mixin;

import dev.tentaworks.rig.Grab;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Pinned mob (held, or set down and still calm) thinks nothing: no goals, targets, navigation, brain or move controls this
 * tick. {@code Mob.serverAiStep} is final and called only from {@code LivingEntity.aiStep}.
 */
@Mixin(Mob.class)
public abstract class MobPinnedMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void tentaworks$pinned(CallbackInfo ci) {
        if (Grab.pinned((Mob) (Object) this)) ci.cancel();
    }
}
