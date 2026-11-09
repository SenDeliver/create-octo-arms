package dev.tentaworks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.tentaworks.rig.FarUse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The crosshair pick (each tick and each frame) keeps a lifted hit only within the Actuators' Reach of the body ({@link FarUse#filter}). */
@Mixin(GameRenderer.class)
public class GameRendererPickMixin {
    @ModifyReturnValue(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;", at = @At("RETURN"))
    private HitResult tentaworks$actuatorReach(HitResult hit, Entity entity, double blockRange, double entityRange, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return entity == mc.player ? FarUse.filter(mc.player, hit, entity.getEyePosition(partialTick)) : hit;
    }
}
