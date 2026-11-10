package dev.tentaworks.rig;

import com.simibubi.create.AllDataComponents;
import dev.tentaworks.registry.TentaworksDataComponents;
import dev.tentaworks.registry.TentaworksItems;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Rig a Wearer deployed from is still worn ({@link WornRig#same}): a creative slot echo is, a swapped Rig is not. */
class WornRigSameTest {
    private static final Holder<Enchantment> FAR_REACH = Holder.Reference.createStandAlone(new HolderOwner<>() {
    }, ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath("tentaworks", "far_reach")));

    private static ItemStack rig() {
        ItemStack s = Actuators.fit(TentaworksItems.NETHERITE_TENTACLE_RIG.asStack());
        s.set(AllDataComponents.BACKTANK_AIR, 900);
        s.set(TentaworksDataComponents.RIG_SETTINGS.get(), new RigSettings(2.5F, 6.0F, RigSettings.ActivationMode.DOUBLE_JUMP, RigSettings.FarUseMode.ALWAYS));
        ItemEnchantments.Mutable e = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        e.set(FAR_REACH, 2);
        s.set(DataComponents.ENCHANTMENTS, e.toImmutable());
        return s;
    }

    private static WornRig chest(ItemStack s) {
        return new WornRig(s, RigSlot.CHEST);
    }

    private static boolean sameAfter(Consumer<ItemStack> change) {
        ItemStack a = rig(), b = a.copy();
        change.accept(b);
        return WornRig.same(chest(a), chest(b));
    }

    @Test
    void theSameStackAndAnEqualCopyAreTheSameRig() {
        ItemStack a = rig();
        assertTrue(WornRig.same(chest(a), chest(a)));
        assertTrue(WornRig.same(chest(a), chest(a.copy())), "the creative inventory's slot echo");
    }

    @Test
    void airAndWearAreReadLiveNotCompared() {
        assertTrue(sameAfter(s -> s.set(AllDataComponents.BACKTANK_AIR, 120)));
        assertTrue(sameAfter(s -> s.setDamageValue(30)));
        ItemStack worn = rig();
        worn.setDamageValue(30);
        assertTrue(WornRig.same(chest(worn), chest(rig())), "repaired back to zero damage");
    }

    @Test
    void anotherRigIsNotTheSame() {
        assertFalse(WornRig.same(chest(rig()), chest(Actuators.fit(TentaworksItems.COPPER_TENTACLE_RIG.asStack()))), "tier");
        assertFalse(sameAfter(s -> s.remove(DataComponents.ENCHANTMENTS)), "enchantments");
        assertFalse(sameAfter(s -> s.set(TentaworksDataComponents.RIG_SETTINGS.get(), RigSettings.DEFAULT)), "Rig Settings");
        assertFalse(sameAfter(s -> s.remove(TentaworksDataComponents.ACTUATORS.get())), "unfitted");
        assertFalse(sameAfter(s -> s.set(DataComponents.CUSTOM_NAME, Component.literal("Doc's"))), "anvil name");
    }

    @Test
    void anotherSlotOrNoRigIsNotTheSame() {
        ItemStack a = rig();
        assertFalse(WornRig.same(chest(a), new WornRig(a, RigSlot.BACK)), "moved to the Back Slot");
        assertFalse(WornRig.same(chest(a), WornRig.NONE), "taken off");
        assertFalse(WornRig.same(WornRig.NONE, WornRig.NONE), "never worn");
        assertFalse(WornRig.same(chest(a), chest(ItemStack.EMPTY)));
    }
}
