package dev.tentaworks.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.client.ponder.TentaworksPonderPlugin;
import dev.tentaworks.config.TentaworksClientConfig;
import dev.tentaworks.rig.RigSneak;
import dev.tentaworks.rig.RigState;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(value = TentaworksIds.MOD_ID, dist = Dist.CLIENT)
public class TentaworksClient {
    public TentaworksClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(RigItemDecorator::register);
        container.registerConfig(ModConfig.Type.CLIENT, TentaworksClientConfig.SPEC);
        PonderIndex.addPlugin(new TentaworksPonderPlugin());
        RigSneak.clientSide(player -> {
            if (player instanceof LocalPlayer) return RigClient.state() != RigState.STOWED;
            RigVisuals.Visual v = RigVisuals.get(player);
            return v != null && v.state() != RigState.STOWED;
        });
    }
}
