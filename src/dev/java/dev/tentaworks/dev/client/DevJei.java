package dev.tentaworks.dev.client;

import dev.tentaworks.TentaworksIds;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/** Dev only: opens JEI recipe views for screenshots. Only reached when JEI is loaded. */
final class DevJei {
    private static final Logger LOG = LoggerFactory.getLogger("tentaworks-script");

    private DevJei() {
    }

    static void open(ItemStack stack, String role, String category) {
        IJeiRuntime rt = DevJeiPlugin.runtime;
        if (rt == null) {
            LOG.info("JEI runtime not ready");
            return;
        }
        RecipeIngredientRole r = role.equals("uses") ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT;
        IFocus<ItemStack> focus = rt.getJeiHelpers().getFocusFactory().createFocus(r, VanillaTypes.ITEM_STACK, stack);
        Optional<RecipeType<?>> type = rt.getJeiHelpers().getRecipeType(new ResourceLocation(category));
        if (type.isEmpty()) {
            LOG.info("JEI no category {}", category);
            return;
        }
        show(rt, type.get(), focus);
    }

    private static <T> void show(IJeiRuntime rt, RecipeType<T> type, IFocus<ItemStack> focus) {
        IRecipeCategory<T> cat = rt.getRecipeManager().getRecipeCategory(type);
        List<T> all = rt.getRecipeManager().createRecipeLookup(type).limitFocus(List.of(focus)).get().toList();
        List<T> ours = all.stream().filter(r -> r instanceof Recipe<?> h && h.getId().getNamespace().equals(TentaworksIds.MOD_ID)).toList();
        List<T> recipes = ours.isEmpty() ? all : ours;
        if (!ours.isEmpty()) LOG.info("JEI {} showing {} of {} (ours only)", type.getUid(), ours.size(), all.size());
        LOG.info("JEI {} {} {} -> {} recipes", type.getUid(), focus.getRole(), focus.getTypedValue().getIngredient(), recipes.size());
        if (!recipes.isEmpty()) rt.getRecipesGui().showRecipes(cat, recipes, List.of(focus));
    }
}
