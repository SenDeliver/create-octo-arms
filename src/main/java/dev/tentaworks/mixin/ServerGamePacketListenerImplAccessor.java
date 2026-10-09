package dev.tentaworks.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The float kick counts from the last move packet; a still wearer sends none, so the count from a long fall would
 * run on after the Rig catches them.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, remap = false)
public interface ServerGamePacketListenerImplAccessor {
    @Accessor("aboveGroundTickCount")
    void tentaworks$setAboveGroundTickCount(int ticks);
}
