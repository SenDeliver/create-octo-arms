package dev.tentaworks.registry;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.equipment.armor.BacktankBlock;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.RigBlock;
import dev.tentaworks.rig.RigTier;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.function.Supplier;

import static dev.tentaworks.Tentaworks.REGISTRATE;

public final class TentaworksBlocks {
    public static final BlockEntry<RigBlock> COPPER_TENTACLE_RIG = rig(TentaworksIds.COPPER_TENTACLE_RIG, RigTier.COPPER,
            SharedProperties::copperMetal, "copper_backtank", () -> TentaworksItems.COPPER_TENTACLE_RIG.get());
    public static final BlockEntry<RigBlock> NETHERITE_TENTACLE_RIG = rig(TentaworksIds.NETHERITE_TENTACLE_RIG, RigTier.NETHERITE,
            SharedProperties::netheriteMetal, "netherite_backtank", () -> TentaworksItems.NETHERITE_TENTACLE_RIG.get());

    private TentaworksBlocks() {
    }

    public static void init() {
    }

    /** Create's backtank block minus {@code CStress.setImpact} (throws for addons). Placed (arms=true): Create's backtank model + folded Tentacles (composite). */
    private static BlockEntry<RigBlock> rig(String name, RigTier tier, NonNullSupplier<Block> base, String createModel, Supplier<ItemLike> drop) {
        return REGISTRATE.block(name, p -> new RigBlock(p, tier))
                .initialProperties(base)
                .blockstate((c, p) -> {
                    ModelFile plain = p.models().getExistingFile(ResourceLocation.fromNamespaceAndPath("create", "block/" + createModel + "/block"));
                    ModelFile arms = p.models().getExistingFile(p.modLoc("block/" + name));
                    p.getVariantBuilder(c.getEntry()).forAllStatesExcept(state -> ConfiguredModel.builder()
                            .modelFile(state.getValue(RigBlock.ARMS) ? arms : plain)
                            .rotationY(((int) state.getValue(BacktankBlock.HORIZONTAL_FACING).toYRot() + 180) % 360)
                            .build(), BlockStateProperties.WATERLOGGED);
                })
                .transform(DisplaySource.displaySource(TentaworksDisplaySources.RIG_AIR))
                .tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .addLayer(() -> RenderType::cutoutMipped)
                .onRegister(b -> BlockStressValues.IMPACTS.register(b, () -> 4.0))
                .loot((lt, block) -> lt.add(block, LootTable.lootTable().withPool(LootPool.lootPool()
                        .when(ExplosionCondition.survivesExplosion())
                        .setRolls(ConstantValue.exactly(1))
                        .add(LootItem.lootTableItem(drop.get())
                                .apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY).include(AllDataComponents.BACKTANK_AIR))))))
                .register();
    }
}
