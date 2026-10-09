package dev.tentaworks.mixin;

import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
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
 * so goggles show it rising.
 */
@Mixin(value = BacktankBlockEntity.class, remap = false)
public abstract class BacktankBlockEntityMixin {
    @Unique
    private int tentaworks$sentAir = -1;

    @Inject(method = "getDefaultName", at = @At("HEAD"), cancellable = true)
    private static void tentaworks$rigName(BlockState state, CallbackInfoReturnable<Component> cir) {
        if (state.getBlock() instanceof RigBlock rig) cir.setReturnValue(rig.getName());
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
