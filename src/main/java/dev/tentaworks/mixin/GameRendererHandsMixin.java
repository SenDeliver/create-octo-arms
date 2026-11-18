package dev.tentaworks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.tentaworks.client.ActuatorRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The first-person Actuators are drawn right before vanilla's hands, outside them, so a mod that replaces the hands keeps them ({@link ActuatorRenderer#beforeHands}). */
@Mixin(GameRenderer.class)
public class GameRendererHandsMixin {
    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V"))
    private void tentaworks$actuatorsFirst(ItemInHandRenderer hands, float partialTick, PoseStack poseStack, MultiBufferSource.BufferSource buffers, LocalPlayer player,
                                           int light, Operation<Void> original) {
        ActuatorRenderer.beforeHands(partialTick, poseStack, buffers, player);
        original.call(hands, partialTick, poseStack, buffers, player, light);
    }
}
