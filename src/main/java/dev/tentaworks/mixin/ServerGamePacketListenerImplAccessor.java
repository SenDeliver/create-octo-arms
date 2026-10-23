package dev.tentaworks.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The float kick counts from the last move packet; a still wearer sends none, so the count runs on while the Rig
 * carries them (reset every carried tick) and, after a long fall, after the Rig catches them.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {
    @Accessor("aboveGroundTickCount")
    void tentaworks$setAboveGroundTickCount(int ticks);

    @Accessor("aboveGroundVehicleTickCount")
    void tentaworks$setAboveGroundVehicleTickCount(int ticks);
}
