package dev.tentaworks.mixin;

import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BacktankBlockEntity.class, remap = false)
public interface BacktankBlockEntityAccessor {
    @Accessor("capacityEnchantLevel")
    int tentaworks$capacityLevel();
}
