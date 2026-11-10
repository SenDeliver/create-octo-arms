package dev.tentaworks.mixin;

import dev.tentaworks.rig.Grab;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Grab moves a held mob with its own teleport packet, which resets the clients' move base but not the tracker's. Without this
 * the tracker's next move after the hold is decoded from the wrong base: a mob set down shows past its spot until the next
 * teleport pulls it back. The tracker takes the position Grab sent as its base too ({@link Grab#sent}).
 */
@Mixin(ServerEntity.class)
public abstract class ServerEntitySentMixin {
    @Shadow
    @Final
    private Entity entity;
    @Shadow
    @Final
    private VecDeltaCodec positionCodec;

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void tentaworks$sent(CallbackInfo ci) {
        Vec3 sent = Grab.sent(entity);
        if (sent != null) positionCodec.setBase(sent);
    }
}
