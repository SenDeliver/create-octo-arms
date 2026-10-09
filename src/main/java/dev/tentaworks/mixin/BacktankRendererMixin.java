package dev.tentaworks.mixin;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.equipment.armor.BacktankRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.tentaworks.rig.RigBlock;
import dev.tentaworks.rig.RigTier;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create picks the copper cogs and shaft for every block that is not its own Netherite Backtank (block renderer,
 * Flywheel shaft visual and the worn-backtank layer all ask here).
 */
@Mixin(value = BacktankRenderer.class, remap = false)
public class BacktankRendererMixin {
    @Inject(method = "getCogsModel", at = @At("HEAD"), cancellable = true)
    private static void tentaworks$netheriteCogs(BlockState state, CallbackInfoReturnable<PartialModel> cir) {
        if (isNetheriteRig(state)) cir.setReturnValue(AllPartialModels.NETHERITE_BACKTANK_COGS);
    }

    @Inject(method = "getShaftModel", at = @At("HEAD"), cancellable = true)
    private static void tentaworks$netheriteShaft(BlockState state, CallbackInfoReturnable<PartialModel> cir) {
        if (isNetheriteRig(state)) cir.setReturnValue(AllPartialModels.NETHERITE_BACKTANK_SHAFT);
    }

    private static boolean isNetheriteRig(BlockState state) {
        return state.getBlock() instanceof RigBlock rig && rig.tier() == RigTier.NETHERITE;
    }
}
