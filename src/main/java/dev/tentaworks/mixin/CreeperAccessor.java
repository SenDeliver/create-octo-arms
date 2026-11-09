package dev.tentaworks.mixin;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A Pinned Creeper's fuse is reset every tick, so even an ignited one never goes off while held or calm. */
@Mixin(Creeper.class)
public interface CreeperAccessor {
    @Accessor("swell")
    void tentaworks$setSwell(int swell);
}
