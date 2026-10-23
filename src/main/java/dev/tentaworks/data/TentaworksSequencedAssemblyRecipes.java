package dev.tentaworks.data;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.data.recipe.SequencedAssemblyRecipeGen;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.registry.TentaworksItems;
import net.minecraft.data.PackOutput;

/** Tentacle: Piston Extension Pole, then deploy a Sticker, press, deploy an Andesite Casing; two loops. */
public class TentaworksSequencedAssemblyRecipes extends SequencedAssemblyRecipeGen {
    GeneratedRecipe TENTACLE = create(TentaworksIds.TENTACLE, b -> b
            .require(AllBlocks.PISTON_EXTENSION_POLE.get())
            .transitionTo(TentaworksItems.INCOMPLETE_TENTACLE.get())
            .addOutput(TentaworksItems.TENTACLE.get(), 1)
            .loops(2)
            .addStep(DeployerApplicationRecipe::new, r -> r.require(AllBlocks.STICKER.get()))
            .addStep(PressingRecipe::new, r -> r)
            .addStep(DeployerApplicationRecipe::new, r -> r.require(AllBlocks.ANDESITE_CASING.get())));

    public TentaworksSequencedAssemblyRecipes(PackOutput output) {
        super(output, TentaworksIds.MOD_ID);
    }
}
