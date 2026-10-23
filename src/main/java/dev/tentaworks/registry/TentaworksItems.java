package dev.tentaworks.registry;

import com.simibubi.create.AllTags;
import com.simibubi.create.content.equipment.armor.AllArmorMaterials;
import com.simibubi.create.content.equipment.armor.BacktankItem;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.RigTier;
import dev.tentaworks.rig.TentacleRigItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;

import static dev.tentaworks.Tentaworks.REGISTRATE;

public final class TentaworksItems {
    public static final ItemEntry<Item> TENTACLE = REGISTRATE.item(TentaworksIds.TENTACLE, Item::new)
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("item/tentacle_arm")))
            .register();
    public static final ItemEntry<SequencedAssemblyItem> INCOMPLETE_TENTACLE = REGISTRATE
            .item(TentaworksIds.INCOMPLETE_TENTACLE, SequencedAssemblyItem::new)
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("item/incomplete_tentacle_arm")))
            .register();

    public static final ItemEntry<BacktankItem.BacktankBlockItem> COPPER_TENTACLE_RIG_PLACEABLE = REGISTRATE
            .item(TentaworksIds.COPPER_TENTACLE_RIG + TentaworksIds.PLACEABLE_SUFFIX,
                    p -> new BacktankItem.BacktankBlockItem(TentaworksBlocks.COPPER_TENTACLE_RIG.get(), TentaworksItems.COPPER_TENTACLE_RIG::get, p))
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/copper_tentacle_rig_item")))
            .register();
    public static final ItemEntry<BacktankItem.BacktankBlockItem> NETHERITE_TENTACLE_RIG_PLACEABLE = REGISTRATE
            .item(TentaworksIds.NETHERITE_TENTACLE_RIG + TentaworksIds.PLACEABLE_SUFFIX,
                    p -> new BacktankItem.BacktankBlockItem(TentaworksBlocks.NETHERITE_TENTACLE_RIG.get(), TentaworksItems.NETHERITE_TENTACLE_RIG::get, p))
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/netherite_tentacle_rig_item")))
            .register();

    public static final ItemEntry<TentacleRigItem> COPPER_TENTACLE_RIG = REGISTRATE
            .item(TentaworksIds.COPPER_TENTACLE_RIG, p -> new TentacleRigItem(AllArmorMaterials.COPPER, p,
                    create("copper_diving"), COPPER_TENTACLE_RIG_PLACEABLE, RigTier.COPPER))
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/copper_tentacle_rig_item")))
            .tag(AllTags.AllItemTags.PRESSURIZED_AIR_SOURCES.tag)
            .register();
    public static final ItemEntry<TentacleRigItem.Layered> NETHERITE_TENTACLE_RIG = REGISTRATE
            .item(TentaworksIds.NETHERITE_TENTACLE_RIG, p -> new TentacleRigItem.Layered(ArmorMaterials.NETHERITE, p,
                    create("netherite_diving"), NETHERITE_TENTACLE_RIG_PLACEABLE, RigTier.NETHERITE))
            .properties(p -> p.fireResistant())
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/netherite_tentacle_rig_item")))
            .tag(AllTags.AllItemTags.PRESSURIZED_AIR_SOURCES.tag)
            .register();

    private TentaworksItems() {
    }

    public static void init() {
    }

    private static ResourceLocation create(String path) {
        return new ResourceLocation("create", path);
    }
}
