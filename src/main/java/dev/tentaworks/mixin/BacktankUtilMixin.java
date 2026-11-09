package dev.tentaworks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.tentaworks.rig.RigAir;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A Rig holds {@code air.capacityFactor} times the Air of a Backtank with the same Capacity (bar, tooltip, refill cap, drain). */
@Mixin(value = BacktankUtil.class, remap = false)
public abstract class BacktankUtilMixin {
    @ModifyReturnValue(method = "maxAir(Lnet/minecraft/world/item/ItemStack;)I", at = @At("RETURN"))
    private static int tentaworks$rigCapacity(int max, ItemStack stack) {
        return RigAir.maxAir(stack, max);
    }
}
