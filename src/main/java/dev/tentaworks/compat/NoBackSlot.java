package dev.tentaworks.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** No Curios: the chest slot is the only place to wear a Rig. */
public final class NoBackSlot implements BackSlot {
    public static final NoBackSlot INSTANCE = new NoBackSlot();

    private NoBackSlot() {
    }

    @Override
    public ItemStack find(LivingEntity entity, Predicate<ItemStack> filter) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean insert(LivingEntity entity, ItemStack stack) {
        return false;
    }
}
