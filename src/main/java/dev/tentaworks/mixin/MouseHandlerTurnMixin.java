package dev.tentaworks.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.tentaworks.client.ShipTurn;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** While the wearer Turns a held Ship the mouse turns the Ship, not the camera ({@link ShipTurn}); the one look call of {@code turnPlayer}. */
@Mixin(MouseHandler.class)
public class MouseHandlerTurnMixin {
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void tentaworks$turnShip(LocalPlayer player, double dx, double dy, Operation<Void> original) {
        if (!ShipTurn.swallow(dx, dy)) original.call(player, dx, dy);
    }
}
