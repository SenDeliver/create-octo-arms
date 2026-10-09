package dev.tentaworks.client.ponder;

import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.registry.TentaworksItems;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiConsumer;

public class TentaworksPonderPlugin implements PonderPlugin {
    public static final ResourceLocation TAG = TentaworksIds.id("tentacle_rigs");

    /** Datagen: Ponder scene and tag text for the lang file. */
    public static void provideLang(BiConsumer<String, String> out) {
        if (PonderIndex.streamPlugins().noneMatch(p -> p.getModId().equals(TentaworksIds.MOD_ID))) PonderIndex.addPlugin(new TentaworksPonderPlugin());
        PonderIndex.getLangAccess().provideLang(TentaworksIds.MOD_ID, out);
    }

    @Override
    public String getModId() {
        return TentaworksIds.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> h = helper.withKeyFunction(RegistryEntry::getId);
        h.forComponents(TentaworksItems.COPPER_TENTACLE_RIG, TentaworksItems.NETHERITE_TENTACLE_RIG, TentaworksItems.TENTACLE,
                        TentaworksItems.COPPER_TENTACLE_RIG_PLACEABLE, TentaworksItems.NETHERITE_TENTACLE_RIG_PLACEABLE)
                .addStoryBoard("tentacle_rig", RigPonderScenes::tentacleRig, TAG);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(TAG)
                .addToIndex()
                .item(TentaworksItems.COPPER_TENTACLE_RIG.get(), true, false)
                .title("Tentacle Rigs")
                .description("Worn Backtanks with four pneumatic Tentacles that carry you over floors, walls and ceilings")
                .register();
        PonderTagRegistrationHelper<ItemProviderEntry<?, ?>> h = helper.withKeyFunction(RegistryEntry::getId);
        h.addToTag(TAG)
                .add(TentaworksItems.COPPER_TENTACLE_RIG)
                .add(TentaworksItems.NETHERITE_TENTACLE_RIG)
                .add(TentaworksItems.TENTACLE);
    }
}
