package dev.tentaworks.client;

import dev.tentaworks.client.ponder.TentaworksPonderPlugin;
import dev.tentaworks.config.TentaworksClientConfig;
import dev.tentaworks.rig.RigSneak;
import dev.tentaworks.rig.RigState;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class TentaworksClient {
    private TentaworksClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(RigItemDecorator::register);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TentaworksClientConfig.SPEC);
        PonderIndex.addPlugin(new TentaworksPonderPlugin());
        RigSneak.clientSide(player -> {
            if (player instanceof LocalPlayer) return RigClient.state() != RigState.STOWED;
            RigVisuals.Visual v = RigVisuals.get(player);
            return v != null && v.state() != RigState.STOWED;
        });
    }
}
