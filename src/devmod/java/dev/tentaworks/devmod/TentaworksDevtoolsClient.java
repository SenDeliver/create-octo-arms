package dev.tentaworks.devmod;

import dev.tentaworks.dev.client.DevRigStats;
import dev.tentaworks.dev.client.DevScript;
import dev.tentaworks.dev.client.GripLineRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;

@Mod(value = "tentaworks_devtools", dist = Dist.CLIENT)
public final class TentaworksDevtoolsClient {
    public TentaworksDevtoolsClient() {
        MinecraftForge.EVENT_BUS.register(DevScript.class);
        MinecraftForge.EVENT_BUS.register(DevRigStats.class);
        MinecraftForge.EVENT_BUS.register(GripLineRenderer.class);
    }
}
