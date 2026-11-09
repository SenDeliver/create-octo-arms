package dev.tentaworks.registry;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.FittingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TentaworksRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> REGISTER = DeferredRegister.create(Registries.RECIPE_SERIALIZER, TentaworksIds.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, FittingRecipe.Serializer> FITTING = REGISTER.register(TentaworksIds.FITTING, FittingRecipe.Serializer::new);

    private TentaworksRecipes() {
    }

    public static void register(IEventBus modBus) {
        REGISTER.register(modBus);
    }
}
