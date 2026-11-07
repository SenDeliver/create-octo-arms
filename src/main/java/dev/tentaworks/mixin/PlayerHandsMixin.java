package dev.tentaworks.mixin;

import dev.tentaworks.rig.RigHands;
import dev.tentaworks.rig.RigSide;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mining speed and sword sweeps of a carried wearer (see {@link RigHands}). {@code getDigSpeed} (the loader's location-aware destroy speed) asks onGround once (the
 * airborne fifth); {@code attack} asks twice, first for the critical hit (ordinal 0, left alone), then for the sweep.
 */
@Mixin(Player.class)
public class PlayerHandsMixin {
    @WrapOperation(method = "getDigSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z"))
    private boolean tentaworks$carriedMines(Player player, Operation<Boolean> original) {
        return RigHands.onGround(original.call(player), RigSide.state(player));
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z", ordinal = 1))
    private boolean tentaworks$carriedSweeps(Player player, Operation<Boolean> original) {
        return RigHands.onGround(original.call(player), RigSide.state(player));
    }
}
