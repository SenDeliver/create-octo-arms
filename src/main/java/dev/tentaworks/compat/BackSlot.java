package dev.tentaworks.compat;

import dev.tentaworks.compat.curios.CuriosBackSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.function.Predicate;

/** The only door to the Curios Back Slot (adr/0006). {@link NoBackSlot} without Curios; the Curios implementation is loaded only when Curios is. */
public interface BackSlot {
    static BackSlot get() {
        return Holder.INSTANCE;
    }

    /** Picks the implementation now (mod construction), so its equip rule and Air source are in place before any world loads. */
    static void init() {
        get();
    }

    /** The first stack in the entity's functional (not cosmetic) back slots that matches, or EMPTY. */
    ItemStack find(LivingEntity entity, Predicate<ItemStack> filter);

    /** Puts the stack into the entity's first empty functional back slot that takes it; false when there is none. */
    boolean insert(LivingEntity entity, ItemStack stack);

    final class Holder {
        private static final BackSlot INSTANCE = ModList.get() != null && ModList.get().isLoaded("curios") ? curios() : NoBackSlot.INSTANCE;

        private Holder() {
        }

        private static BackSlot curios() {
            return CuriosBackSlot.create();
        }
    }
}
