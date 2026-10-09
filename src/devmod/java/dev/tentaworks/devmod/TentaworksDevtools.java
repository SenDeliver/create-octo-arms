package dev.tentaworks.devmod;

import dev.tentaworks.dev.DevTools;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** The dev source set's @EventBusSubscriber classes name mod "tentaworks", so a separate jar registers them by hand. */
@Mod("tentaworks_devtools")
public final class TentaworksDevtools {
    public TentaworksDevtools() {
        NeoForge.EVENT_BUS.register(DevTools.class);
    }
}
