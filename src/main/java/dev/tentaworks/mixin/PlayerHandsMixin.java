package dev.tentaworks.mixin;

import dev.tentaworks.rig.RigHands;
import dev.tentaworks.rig.RigSide;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mining speed and sword sweeps of a carried wearer (see {@link RigHands}). {@code getDigSpeed} (the loader's location-aware destroy speed) asks onGround once (the
 * airborne fifth); {@code attack} asks twice, first for the critical hit (ordinal 0, left alone), then for the sweep.
 */
@Mixin(Player.class)
public class PlayerHandsMixin {
    @Redirect(method = "getDigSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z"))
    private boolean tentaworks$carriedMines(Player player) {
        return RigHands.onGround(player.onGround(), RigSide.state(player));
    }

    @Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z", ordinal = 1))
    private boolean tentaworks$carriedSweeps(Player player) {
        return RigHands.onGround(player.onGround(), RigSide.state(player));
    }
}
