package dev.tentaworks.devmod;

import dev.tentaworks.dev.client.DevRigStats;
import dev.tentaworks.dev.client.DevScript;
import dev.tentaworks.dev.client.GripLineRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "tentaworks_devtools", dist = Dist.CLIENT)
public final class TentaworksDevtoolsClient {
    public TentaworksDevtoolsClient() {
        NeoForge.EVENT_BUS.register(DevScript.class);
        NeoForge.EVENT_BUS.register(DevRigStats.class);
        NeoForge.EVENT_BUS.register(GripLineRenderer.class);
    }
}
