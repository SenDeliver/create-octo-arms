package dev.tentaworks.rig;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.tentaworks.registry.TentaworksItems;
import dev.tentaworks.registry.TentaworksRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * Fitting at a crafting table (recipe type {@code tentaworks:fitting}): Actuator, Rig, Actuator in one row. The result is that
 * same Rig with the Actuators: its Air, enchantments, Rig Settings, damage and name stay. A fitted Rig does not match. Shaped,
 * so the recipe book and JEI show the row as it is.
 */
public class FittingRecipe extends ShapedRecipe {
    private final Item rig;

    public FittingRecipe(CraftingBookCategory category, Item rig) {
        super("", category, ShapedRecipePattern.of(Map.of('A', Ingredient.of(TentaworksItems.ACTUATOR.get()), 'R', Ingredient.of(rig)), "ARA"),
                Actuators.fit(new ItemStack(rig)));
        this.rig = rig;
    }

    public Item rig() {
        return rig;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return super.matches(input, level) && !Actuators.fitted(rigIn(input));
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack in = rigIn(input);
        return in.isEmpty() || Actuators.fitted(in) ? ItemStack.EMPTY : Actuators.fit(in);
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(TentaworksItems.ACTUATOR.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return TentaworksRecipes.FITTING.get();
    }

    private ItemStack rigIn(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.is(rig)) return s;
        }
        return ItemStack.EMPTY;
    }

    public static class Serializer implements RecipeSerializer<FittingRecipe> {
        private static final MapCodec<FittingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.EQUIPMENT).forGetter(FittingRecipe::category),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("rig").forGetter(FittingRecipe::rig)
        ).apply(i, FittingRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, FittingRecipe> STREAM_CODEC = StreamCodec.composite(
                CraftingBookCategory.STREAM_CODEC, FittingRecipe::category,
                ByteBufCodecs.registry(Registries.ITEM), FittingRecipe::rig,
                FittingRecipe::new);

        @Override
        public MapCodec<FittingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FittingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
