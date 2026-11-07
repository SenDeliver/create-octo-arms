package dev.tentaworks.mixin;

import dev.tentaworks.rig.RigSneak;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A sneaking wearer with the Rig out keeps the standing pose and is not held back at edges (see {@link RigSneak}). */
@Mixin(Player.class)
public class PlayerSneakMixin {
    @WrapOperation(method = "updatePlayerPose", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isShiftKeyDown()Z"))
    private boolean tentaworks$noCrouchPose(Player player, Operation<Boolean> original) {
        return original.call(player) && !RigSneak.deployed(player);
    }

    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void tentaworks$noEdgeHold(CallbackInfoReturnable<Boolean> cir) {
        if (RigSneak.deployed((Player) (Object) this)) cir.setReturnValue(false);
    }
}
