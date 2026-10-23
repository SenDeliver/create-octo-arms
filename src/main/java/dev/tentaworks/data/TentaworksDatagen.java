package dev.tentaworks.data;

import dev.tentaworks.client.ponder.TentaworksPonderPlugin;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.registry.TentaworksItems;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.SmithingTransformRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.Tags;
import net.minecraftforge.data.event.GatherDataEvent;

import static dev.tentaworks.Tentaworks.REGISTRATE;

/** Recipes (design §6): Tentacle by Sequenced Assembly, Rigs by Mechanical Crafting, Netherite Rig also by smithing like Create's backtank. */
public final class TentaworksDatagen {
    private TentaworksDatagen() {
    }

    public static void register(IEventBus modBus) {
        REGISTRATE.addDataGenerator(ProviderType.RECIPE, TentaworksDatagen::smithing);
        REGISTRATE.addDataGenerator(ProviderType.LANG, prov -> TentaworksPonderPlugin.provideLang(prov::add));
        modBus.addListener(TentaworksDatagen::gather);
    }

    private static void smithing(RegistrateRecipeProvider prov) {
        SmithingTransformRecipeBuilder.smithing(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                        Ingredient.of(TentaworksItems.COPPER_TENTACLE_RIG.get()), Ingredient.of(Tags.Items.INGOTS_NETHERITE),
                        RecipeCategory.COMBAT, TentaworksItems.NETHERITE_TENTACLE_RIG.get())
                .unlocks("has_copper_tentacle_rig", RegistrateRecipeProvider.has(TentaworksItems.COPPER_TENTACLE_RIG.get()))
                .save(prov, TentaworksIds.id("smithing/" + TentaworksIds.NETHERITE_TENTACLE_RIG));
    }

    private static void gather(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();
        PackOutput out = gen.getPackOutput();
        gen.addProvider(event.includeServer(), new TentaworksSequencedAssemblyRecipes(out));
        gen.addProvider(event.includeServer(), new TentaworksMechanicalCraftingRecipes(out));
    }
}
