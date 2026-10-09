package dev.tentaworks.registry;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.RigSettings;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TentaworksDataComponents {
    private static final DeferredRegister<DataComponentType<?>> REGISTER = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, TentaworksIds.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RigSettings>> RIG_SETTINGS = REGISTER.register(TentaworksIds.RIG_SETTINGS,
            () -> DataComponentType.<RigSettings>builder().persistent(RigSettings.CODEC).networkSynchronized(RigSettings.STREAM_CODEC).build());

    private TentaworksDataComponents() {
    }

    public static void register(IEventBus modBus) {
        REGISTER.register(modBus);
    }
}
