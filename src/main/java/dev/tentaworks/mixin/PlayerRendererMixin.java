package dev.tentaworks.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.tentaworks.client.ActuatorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** While the Right Actuator carries the main-hand item ({@link ActuatorRenderer#carries}), the main arm poses empty (no raised bow, shield or spyglass arm). */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @ModifyReturnValue(method = "getArmPose(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At("RETURN"))
    private static HumanoidModel.ArmPose tentaworks$carriedArm(HumanoidModel.ArmPose pose, AbstractClientPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && ActuatorRenderer.carries(player) ? HumanoidModel.ArmPose.EMPTY : pose;
    }
}
