package dev.tentaworks.mixin;

import dev.tentaworks.rig.RigSneak;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shift with the Rig out moves the wearer down a ladder instead of holding them on it (see {@link RigSneak}). */
@Mixin(LivingEntity.class)
public class LivingEntitySneakMixin {
    @Inject(method = "isSuppressingSlidingDownLadder", at = @At("HEAD"), cancellable = true)
    private void tentaworks$slideWithRig(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && RigSneak.deployed(player)) cir.setReturnValue(false);
    }
}
