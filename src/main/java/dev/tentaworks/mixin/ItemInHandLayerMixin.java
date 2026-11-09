package dev.tentaworks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.tentaworks.client.ActuatorRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** While a Far Use carries the main-hand item at the Right Actuator's tip, the hand shows it empty (third person). */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack tentaworks$itemAtTip(LivingEntity entity, Operation<ItemStack> original) {
        ItemStack stack = original.call(entity);
        return ActuatorRenderer.itemAtTip(entity) ? ItemStack.EMPTY : stack;
    }
}
