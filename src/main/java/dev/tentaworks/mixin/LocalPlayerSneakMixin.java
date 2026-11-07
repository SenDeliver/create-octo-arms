package dev.tentaworks.mixin;

import dev.tentaworks.client.RigClient;
import dev.tentaworks.rig.RigSneak;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Rig swallows Shift as movement input, so the wearer's sneak is reported from {@link RigClient#sneaking()} instead: the
 * server and use/interaction see a sneak, while the crouch (camera, slow walk) stays off (see {@link RigSneak}).
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerSneakMixin {
    @Inject(method = "isShiftKeyDown", at = @At("HEAD"), cancellable = true)
    private void tentaworks$rigSneak(CallbackInfoReturnable<Boolean> cir) {
        if (RigClient.sneaking()) cir.setReturnValue(true);
    }

    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isShiftKeyDown()Z"))
    private boolean tentaworks$noCrouch(LocalPlayer player, Operation<Boolean> original) {
        return original.call(player) && !RigSneak.deployed(player);
    }
}
