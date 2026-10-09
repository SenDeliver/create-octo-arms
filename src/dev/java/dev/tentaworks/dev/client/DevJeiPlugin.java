package dev.tentaworks.dev.client;

import dev.tentaworks.TentaworksIds;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/** Dev only: keeps the JEI runtime for DevScript `jei`. The real mod has no JEI code: Create's plugin shows our recipes. */
@JeiPlugin
public class DevJeiPlugin implements IModPlugin {
    static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return TentaworksIds.id("dev_jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }
}
