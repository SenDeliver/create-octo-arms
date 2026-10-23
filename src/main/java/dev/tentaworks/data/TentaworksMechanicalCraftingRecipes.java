package dev.tentaworks.data;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.data.recipe.MechanicalCraftingRecipeBuilder;
import com.simibubi.create.api.data.recipe.MechanicalCraftingRecipeGen;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.registry.TentaworksItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

/** Rigs: tank in the middle, Tentacles at the corners, Precision Mechanism on top (design §6). A crafted Rig starts empty. */
public class TentaworksMechanicalCraftingRecipes extends MechanicalCraftingRecipeGen {
    static final TagKey<Item> COPPER_PLATES = ItemTags.create(new ResourceLocation("forge", "plates/copper"));

    GeneratedRecipe COPPER_TENTACLE_RIG = create(TentaworksItems.COPPER_TENTACLE_RIG::get).recipe(b -> rig(b, AllItems.COPPER_BACKTANK.get()));
    GeneratedRecipe NETHERITE_TENTACLE_RIG = create(TentaworksItems.NETHERITE_TENTACLE_RIG::get).recipe(b -> rig(b, AllItems.NETHERITE_BACKTANK.get()));

    public TentaworksMechanicalCraftingRecipes(PackOutput output) {
        super(output, TentaworksIds.MOD_ID);
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
