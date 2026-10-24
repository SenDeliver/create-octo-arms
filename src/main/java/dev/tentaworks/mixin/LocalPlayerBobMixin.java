package dev.tentaworks.mixin;

import dev.tentaworks.client.RigClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A carried wearer does not walk, so the view does not bob: vanilla {@code Player.aiStep} feeds {@code bob} from onGround and
 * the horizontal motion, which a Rig move with the body on the floor also produces (camera and hand shake with View Bobbing on).
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerBobMixin {
    @Inject(method = "aiStep", at = @At("TAIL"))
    private void tentaworks$noBobWhileCarried(CallbackInfo ci) {
        if (!RigClient.state().carries()) return;
        LocalPlayer player = (LocalPlayer) (Object) this;
        player.oBob = 0.0F;
        player.bob = 0.0F;
    }
}
