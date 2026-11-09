package dev.tentaworks.mixin;

import dev.tentaworks.rig.FarUse;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An Armor Stand is used through {@code interactAt} (a consumed one skips {@code interactOn}): a Far Use once it swapped an item. */
@Mixin(ArmorStand.class)
public class ArmorStandFarUseMixin {
    @Inject(method = "interactAt", at = @At("RETURN"))
    private void tentaworks$farInteracted(Player player, Vec3 at, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer p) FarUse.usedOn(p, (ArmorStand) (Object) this, hand, cir.getReturnValue());
    }
}
