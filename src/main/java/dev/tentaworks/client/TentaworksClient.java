package dev.tentaworks.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.client.ponder.TentaworksPonderPlugin;
import dev.tentaworks.compat.curios.CuriosClient;
import dev.tentaworks.config.TentaworksClientConfig;
import dev.tentaworks.rig.RigSide;
import dev.tentaworks.rig.RigState;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = TentaworksIds.MOD_ID, dist = Dist.CLIENT)
public class TentaworksClient {
    public TentaworksClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(RigItemDecorator::register);
        container.registerConfig(ModConfig.Type.CLIENT, TentaworksClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mc, parent) -> new ConfigurationScreen(container, parent));
        PonderIndex.addPlugin(new TentaworksPonderPlugin());
        if (ModList.get().isLoaded("curios")) curios(modBus);
        RigSide.clientSide(player -> {
            if (player instanceof LocalPlayer) return RigClient.state();
            RigVisuals.Visual v = RigVisuals.get(player);
            return v == null ? RigState.STOWED : v.state();
        });
    }

    /** Loaded only when Curios is: the Back Slot renderer. */
    private static void curios(IEventBus modBus) {
        CuriosClient.init(modBus);
    }
}
