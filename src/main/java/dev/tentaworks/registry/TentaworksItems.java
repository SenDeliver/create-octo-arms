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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;

import static dev.tentaworks.Tentaworks.REGISTRATE;

public final class TentaworksItems {
    public static final TagKey<Item> RIG_ENCHANTABLE = TagKey.create(Registries.ITEM, TentaworksIds.id(TentaworksIds.RIG_ENCHANTABLE));

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
            .properties(p -> p.durability(ArmorItem.Type.CHESTPLATE.getDurability(7)))
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/copper_tentacle_rig_item")))
            .tag(AllTags.AllItemTags.PRESSURIZED_AIR_SOURCES.tag, ItemTags.CHEST_ARMOR, RIG_ENCHANTABLE)
            .register();
    public static final ItemEntry<TentacleRigItem.Layered> NETHERITE_TENTACLE_RIG = REGISTRATE
            .item(TentaworksIds.NETHERITE_TENTACLE_RIG, p -> new TentacleRigItem.Layered(ArmorMaterials.NETHERITE, p,
                    create("netherite_diving"), NETHERITE_TENTACLE_RIG_PLACEABLE, RigTier.NETHERITE))
            .properties(p -> p.fireResistant().durability(ArmorItem.Type.CHESTPLATE.getDurability(37)))
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/netherite_tentacle_rig_item")))
            .tag(AllTags.AllItemTags.PRESSURIZED_AIR_SOURCES.tag, ItemTags.CHEST_ARMOR, RIG_ENCHANTABLE)
            .register();

    private TentaworksItems() {
    }

    public static void init() {
    }

    private static ResourceLocation create(String path) {
        return ResourceLocation.fromNamespaceAndPath("create", path);
    }
}
