package dev.tentaworks.mixin;

import dev.tentaworks.rig.RigSneak;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A sneaking wearer with the Rig out keeps the standing pose and is not held back at edges (see {@link RigSneak}). */
@Mixin(Player.class)
public class PlayerSneakMixin {
    @Redirect(method = "updatePlayerPose", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isShiftKeyDown()Z"))
    private boolean tentaworks$noCrouchPose(Player player) {
        return player.isShiftKeyDown() && !RigSneak.deployed(player);
    }

    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void tentaworks$noEdgeHold(CallbackInfoReturnable<Boolean> cir) {
        if (RigSneak.deployed((Player) (Object) this)) cir.setReturnValue(false);
    }
}
