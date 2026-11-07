package dev.tentaworks.mixin;

import dev.tentaworks.rig.WornRig;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Curios items never wear through {@code hurtArmor}: a Back Slot Rig wears here, with the same damage and the same
 * armor-bypass gate as the chest piece (only called for damage armor stops), so the 1-durability rule holds in both slots (review A8).
 */
@Mixin(Player.class)
public class PlayerBackWearMixin {
    @Inject(method = "hurtArmor", at = @At("HEAD"))
    private void tentaworks$wearBackSlotRig(DamageSource source, float damage, CallbackInfo ci) {
        WornRig.wearBack((Player) (Object) this, source, damage);
    }
}
