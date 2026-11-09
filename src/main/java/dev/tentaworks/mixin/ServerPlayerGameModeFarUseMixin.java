package dev.tentaworks.mixin;

import dev.tentaworks.rig.FarUse;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A use on a block (the block's own use, the item's use on it, a placement) is a Far Use only once it did something (see {@link FarUse#usedOn}). */
@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeFarUseMixin {
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void tentaworks$farUsedOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        FarUse.usedOn(player, hit, cir.getReturnValue());
    }
}
