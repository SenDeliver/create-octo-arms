package dev.tentaworks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import dev.tentaworks.rig.RigAir;
import dev.tentaworks.rig.RigBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create names every backtank block entity "Copper Backtank"; a placed Rig keeps its own name.
 * Create only syncs a filling backtank's Air when it is full; a Rig Block syncs it every 10 ticks while it changes,
 * so goggles show it rising. A Rig Block fills to the Rig's own max Air ({@code air.capacityFactor} times the Backtank's).
 */
@Mixin(value = BacktankBlockEntity.class, remap = false)
public abstract class BacktankBlockEntityMixin {
    @Unique
    private int tentaworks$sentAir = -1;

    @Inject(method = "getDefaultName", at = @At("HEAD"), cancellable = true)
    private static void tentaworks$rigName(BlockState state, CallbackInfoReturnable<Component> cir) {
        if (state.getBlock() instanceof RigBlock rig) cir.setReturnValue(rig.getName());
    }

    @ModifyExpressionValue(method = {"tick", "getComparatorOutput", "read"},
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/equipment/armor/BacktankUtil;maxAir(I)I"))
    private int tentaworks$rigCapacity(int max) {
        return ((BacktankBlockEntity) (Object) this).getBlockState().getBlock() instanceof RigBlock ? RigAir.capacity(max) : max;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void tentaworks$syncAir(CallbackInfo ci) {
        BacktankBlockEntity be = (BacktankBlockEntity) (Object) this;
        if (be.getLevel() == null || be.getLevel().isClientSide || !(be.getBlockState().getBlock() instanceof RigBlock)) return;
        if (be.getLevel().getGameTime() % 10 != 0 || be.getAirLevel() == tentaworks$sentAir) return;
        tentaworks$sentAir = be.getAirLevel();
        be.sendData();
    }
}
