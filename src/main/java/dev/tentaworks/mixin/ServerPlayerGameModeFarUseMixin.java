package dev.tentaworks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.tentaworks.rig.DualWield;
import dev.tentaworks.rig.FarUse;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A use on a block (the block's own use, the item's use on it, a placement) is a Far Use only once it did something (see
 * {@link FarUse#usedOn}). A survival block break wears the main-hand tool through its one {@code mineBlock} call; the off-hand
 * tool that helped break it ({@link DualWield#mined}) wears there too.
 */
@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeFarUseMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void tentaworks$farUsedOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        FarUse.usedOn(player, hit, hand, cir.getReturnValue());
    }

    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;mineBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"))
    private void tentaworks$dualMined(ItemStack stack, Level level, BlockState state, BlockPos pos, Player who, Operation<Void> original) {
        original.call(stack, level, state, pos, who);
        DualWield.mined(player, level, state, pos);
    }
}
