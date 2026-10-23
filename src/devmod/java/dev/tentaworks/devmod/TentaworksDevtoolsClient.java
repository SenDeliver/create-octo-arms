package dev.tentaworks.devmod;

import dev.tentaworks.dev.client.DevRigStats;
import dev.tentaworks.dev.client.DevScript;
import dev.tentaworks.dev.client.GripLineRenderer;
import net.minecraftforge.common.MinecraftForge;

public final class TentaworksDevtoolsClient {
    private TentaworksDevtoolsClient() {
    }

    static void init() {
        MinecraftForge.EVENT_BUS.register(DevScript.class);
        MinecraftForge.EVENT_BUS.register(DevRigStats.class);
        MinecraftForge.EVENT_BUS.register(GripLineRenderer.class);
    }
}
