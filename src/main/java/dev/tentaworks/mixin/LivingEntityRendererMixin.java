package dev.tentaworks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.tentaworks.client.ActuatorRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A swing of a hand its Actuator stands in for ({@link ActuatorRenderer#swings}) is the Actuator's: the body's arm stays still. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @ModifyReturnValue(method = "getAttackAnim(Lnet/minecraft/world/entity/LivingEntity;F)F", at = @At("RETURN"))
    private float tentaworks$actuatorSwings(float swing, LivingEntity entity, float partialTick) {
        return swing > 0 && ActuatorRenderer.swings(entity) ? 0 : swing;
    }
}
