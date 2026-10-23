package dev.tentaworks;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import dev.tentaworks.compat.ShipFrames;
import dev.tentaworks.config.TentaworksConfig;
import dev.tentaworks.data.TentaworksDatagen;
import dev.tentaworks.network.TentaworksNetwork;
import dev.tentaworks.registry.TentaworksBlocks;
import dev.tentaworks.registry.TentaworksCreativeTabs;
import dev.tentaworks.registry.TentaworksDisplaySources;
import dev.tentaworks.registry.TentaworksItems;
import dev.tentaworks.registry.TentaworksLang;
import dev.tentaworks.rig.RigServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

@Mod(TentaworksIds.MOD_ID)
public class Tentaworks {
    // Registrate's default tab is SEARCH, which already holds every item: adding them again crashes the creative inventory.
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(TentaworksIds.MOD_ID)
            .defaultCreativeTab((ResourceKey<CreativeModeTab>) null);

    public Tentaworks(IEventBus modBus, ModContainer container) {
        REGISTRATE.registerEventListeners(modBus);
        REGISTRATE.setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                .andThen(TooltipModifier.mapNull(KineticStats.create(item))));
        TentaworksDisplaySources.init();
        TentaworksBlocks.init();
        TentaworksItems.init();
        TentaworksCreativeTabs.register(modBus);
        TentaworksLang.init();
        TentaworksDatagen.register(modBus);
        modBus.addListener(TentaworksNetwork::register);
        modBus.addListener((BlockEntityTypeAddBlocksEvent e) -> e.modify(AllBlockEntityTypes.BACKTANK.get(),
                TentaworksBlocks.COPPER_TENTACLE_RIG.get(), TentaworksBlocks.NETHERITE_TENTACLE_RIG.get()));
        container.registerConfig(ModConfig.Type.SERVER, TentaworksConfig.SPEC);
        RigServer.init();
        ShipFrames.init();
    }
}
