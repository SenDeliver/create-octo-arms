package dev.tentaworks.devmod;

import dev.tentaworks.dev.DevTools;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/** The dev source set's @EventBusSubscriber classes name mod "tentaworks", so a separate jar registers them by hand. */
@Mod("tentaworks_devtools")
public final class TentaworksDevtools {
    public TentaworksDevtools() {
        MinecraftForge.EVENT_BUS.register(DevTools.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> TentaworksDevtoolsClient::init);
    }
}
