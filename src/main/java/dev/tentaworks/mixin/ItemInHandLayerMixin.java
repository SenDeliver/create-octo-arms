package dev.tentaworks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.tentaworks.client.ActuatorRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** While an Actuator carries a hand's item ({@link ActuatorRenderer#carries}), that hand shows empty (third person). */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    private static final String RENDER = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V";

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack tentaworks$carriedMain(LivingEntity entity, Operation<ItemStack> original) {
        ItemStack stack = original.call(entity);
        return ActuatorRenderer.carries(entity, InteractionHand.MAIN_HAND) ? ItemStack.EMPTY : stack;
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack tentaworks$carriedOff(LivingEntity entity, Operation<ItemStack> original) {
        ItemStack stack = original.call(entity);
        return ActuatorRenderer.carries(entity, InteractionHand.OFF_HAND) ? ItemStack.EMPTY : stack;
    }
}
