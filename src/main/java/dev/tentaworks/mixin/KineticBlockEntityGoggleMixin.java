package dev.tentaworks.mixin;

import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.tentaworks.rig.RigGoggles;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Goggles on a placed Rig Block: Air, refill rate, Reach, top Speed (Create's backtank block entity has no goggle text). */
@Mixin(value = KineticBlockEntity.class, remap = false)
public class KineticBlockEntityGoggleMixin {
    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"), cancellable = true)
    private void tentaworks$rigGoggles(List<Component> tooltip, boolean sneaking, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof BacktankBlockEntity be && RigGoggles.add(be, tooltip, cir.getReturnValueZ())) cir.setReturnValue(true);
    }
}
