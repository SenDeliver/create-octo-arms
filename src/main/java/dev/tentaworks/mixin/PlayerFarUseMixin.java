package dev.tentaworks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.tentaworks.rig.FarUse;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A use on an entity, and a hit, are Far Uses only once they did something (see {@link FarUse#usedOn}). {@code attack} hurts
 * its main target through {@code Entity.hurt} once; the sweep hurts the others through {@code LivingEntity.hurt}, left alone.
 */
@Mixin(Player.class)
public class PlayerFarUseMixin {
    @Inject(method = "interactOn", at = @At("RETURN"))
    private void tentaworks$farInteracted(Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if ((Object) this instanceof ServerPlayer p) FarUse.usedOn(p, target, hand, cir.getReturnValue());
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean tentaworks$farHit(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        boolean hurt = original.call(target, source, amount);
        if (hurt && (Object) this instanceof ServerPlayer p) FarUse.hit(p, target);
        return hurt;
    }
}
