package dev.tentaworks.devmod;

import dev.tentaworks.dev.DevTools;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;

/** The dev source set's @EventBusSubscriber classes name mod "tentaworks", so a separate jar registers them by hand. */
@Mod("tentaworks_devtools")
public final class TentaworksDevtools {
    public TentaworksDevtools() {
        MinecraftForge.EVENT_BUS.register(DevTools.class);
    }
}
