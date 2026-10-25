package dev.tentaworks.mixin;

import dev.tentaworks.rig.RigEnchants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A wearer with Silent Step and the Rig out dampens their own vibrations, like an item on wool: no Sculk Sensor, Shrieker or Warden hears them. */
@Mixin(Entity.class)
public class EntityVibrationMixin {
    @Inject(method = "dampensVibrations", at = @At("HEAD"), cancellable = true)
    private void tentaworks$silentStep(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && RigEnchants.silent(player)) cir.setReturnValue(true);
    }
}
