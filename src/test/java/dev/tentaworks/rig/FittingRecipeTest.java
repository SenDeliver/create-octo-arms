package dev.tentaworks.rig;

import com.simibubi.create.AllDataComponents;
import dev.tentaworks.registry.TentaworksDataComponents;
import dev.tentaworks.registry.TentaworksItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fitting: Actuator, Rig, Actuator in one row of a 3-wide grid; the Rig comes out with everything it had plus the Actuators. */
class FittingRecipeTest {
    private static FittingRecipe copper() {
        return new FittingRecipe(CraftingBookCategory.EQUIPMENT, TentaworksItems.COPPER_TENTACLE_RIG.get());
    }

    private static ItemStack actuator() {
        return TentaworksItems.ACTUATOR.asStack();
    }

    /** A 3x3 grid, row by row; null is an empty cell. */
    private static CraftingInput grid(ItemStack... cells) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < 9; i++) list.add(i < cells.length && cells[i] != null ? cells[i] : ItemStack.EMPTY);
        return CraftingInput.of(3, 3, list);
    }

    private static ItemStack used() {
        ItemStack rig = TentaworksItems.COPPER_TENTACLE_RIG.asStack();
        rig.set(AllDataComponents.BACKTANK_AIR, 640);
        rig.setDamageValue(30);
        rig.set(TentaworksDataComponents.RIG_SETTINGS.get(), new RigSettings(2.5F, 6.1F, RigSettings.ActivationMode.KEY, RigSettings.FarUseMode.OFF));
        rig.set(DataComponents.CUSTOM_NAME, Component.literal("Octo"));
        return rig;
    }

    @Test
    void anyRowMatches() {
        ItemStack r = used();
        assertTrue(copper().matches(grid(actuator(), r, actuator()), null));
        assertTrue(copper().matches(grid(null, null, null, actuator(), r, actuator()), null));
        assertTrue(copper().matches(grid(null, null, null, null, null, null, actuator(), r, actuator()), null));
    }

    @Test
    void anythingElseDoesNot() {
        ItemStack r = used();
        assertFalse(copper().matches(grid(actuator(), null, null, r, null, null, actuator()), null));
        assertFalse(copper().matches(grid(actuator(), r), null));
        assertFalse(copper().matches(grid(actuator(), r, actuator(), actuator()), null));
        assertFalse(copper().matches(grid(actuator(), TentaworksItems.NETHERITE_TENTACLE_RIG.asStack(), actuator()), null));
        assertFalse(copper().matches(grid(actuator(), Actuators.fit(r), actuator()), null));
        assertFalse(copper().canCraftInDimensions(2, 2));
    }

    @Test
    void theRigKeepsEverything() {
        ItemStack r = used();
        ItemStack out = copper().assemble(grid(actuator(), r, actuator()), null);
        assertTrue(Actuators.fitted(out));
        assertEquals(1, out.getCount());
        for (TypedDataComponent<?> c : r.getComponents())
            assertEquals(c.value(), out.get(c.type()), c.type().toString());
        assertEquals(640, out.get(AllDataComponents.BACKTANK_AIR));
        assertEquals(30, out.getDamageValue());
        assertEquals(RigSettings.FarUseMode.OFF, out.get(TentaworksDataComponents.RIG_SETTINGS.get()).farUse());
        assertFalse(Actuators.fitted(r));
    }

    @Test
    void shownWithTheFittedRig() {
        assertTrue(Actuators.fitted(copper().getResultItem(null)));
        assertTrue(copper().getToastSymbol().is(TentaworksItems.ACTUATOR.get()));
        assertEquals(3, copper().getIngredients().size());
    }
}
