package dev.tentaworks.data;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.data.recipe.MechanicalCraftingRecipeBuilder;
import com.simibubi.create.api.data.recipe.MechanicalCraftingRecipeGen;
import dev.tentaworks.TentaworksIds;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import dev.tentaworks.registry.TentaworksItems;
import dev.tentaworks.rig.Actuators;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Rigs: tank in the middle, Tentacles at the corners, Precision Mechanism on top (design §6). A crafted Rig starts empty.
 * The fitted variants add an Actuator on each side and build the Rig with its Actuators (Create's builder takes no components,
 * so these recipes are built directly).
 */
public class TentaworksMechanicalCraftingRecipes extends MechanicalCraftingRecipeGen {
    static final TagKey<Item> COPPER_PLATES = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "plates/copper"));

    GeneratedRecipe COPPER_TENTACLE_RIG = create(TentaworksItems.COPPER_TENTACLE_RIG::get).recipe(b -> rig(b, AllItems.COPPER_BACKTANK.get()));
    GeneratedRecipe NETHERITE_TENTACLE_RIG = create(TentaworksItems.NETHERITE_TENTACLE_RIG::get).recipe(b -> rig(b, AllItems.NETHERITE_BACKTANK.get()));

    public TentaworksMechanicalCraftingRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, TentaworksIds.MOD_ID);
    }

    @Override
    public void buildRecipes(RecipeOutput output) {
        super.buildRecipes(output);
        fitted(output, TentaworksItems.COPPER_TENTACLE_RIG.get(), AllItems.COPPER_BACKTANK.get());
        fitted(output, TentaworksItems.NETHERITE_TENTACLE_RIG.get(), AllItems.NETHERITE_BACKTANK.get());
    }

    private static void fitted(RecipeOutput output, Item rig, ItemLike backtank) {
        ShapedRecipePattern pattern = ShapedRecipePattern.of(Map.of(
                'T', Ingredient.of(TentaworksItems.TENTACLE.get()),
                'P', Ingredient.of(AllItems.PRECISION_MECHANISM.get()),
                'B', Ingredient.of(AllBlocks.BRASS_CASING.get()),
                'K', Ingredient.of(backtank),
                'C', Ingredient.of(COPPER_PLATES),
                'A', Ingredient.of(TentaworksItems.ACTUATOR.get())
        ), List.of(" TPT ", "ABKBA", " TCT "));
        ItemStack result = Actuators.fit(new ItemStack(rig));
        output.accept(TentaworksIds.id("mechanical_crafting/fitted_" + rig.builtInRegistryHolder().key().location().getPath()),
                new MechanicalCraftingRecipe("", CraftingBookCategory.MISC, pattern, result, true), null);
    }

    private static MechanicalCraftingRecipeBuilder rig(MechanicalCraftingRecipeBuilder b, ItemLike backtank) {
        return b.key('T', TentaworksItems.TENTACLE.get())
                .key('P', AllItems.PRECISION_MECHANISM.get())
                .key('B', AllBlocks.BRASS_CASING.get())
                .key('K', backtank)
                .key('C', COPPER_PLATES)
                .patternLine("TPT")
                .patternLine("BKB")
                .patternLine("TCT");
    }
}
