package dev.tentaworks.registry;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.RigSettings;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TentaworksDataComponents {
    private static final DeferredRegister<DataComponentType<?>> REGISTER = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, TentaworksIds.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RigSettings>> RIG_SETTINGS = REGISTER.register(TentaworksIds.RIG_SETTINGS,
            () -> DataComponentType.<RigSettings>builder().persistent(RigSettings.CODEC).networkSynchronized(RigSettings.STREAM_CODEC).build());

    /** Present = the Rig has its two Actuators (Fitting). No value: a fitted Rig cannot be unfitted. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> ACTUATORS = REGISTER.register(TentaworksIds.ACTUATORS,
            () -> DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)).build());

    private TentaworksDataComponents() {
    }

    public static void register(IEventBus modBus) {
        REGISTER.register(modBus);
    }
}
