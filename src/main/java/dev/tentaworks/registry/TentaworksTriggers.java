package dev.tentaworks.registry;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.RigEventTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TentaworksTriggers {
    private static final DeferredRegister<CriterionTrigger<?>> REGISTER = DeferredRegister.create(Registries.TRIGGER_TYPE, TentaworksIds.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, RigEventTrigger> RIG_EVENT = REGISTER.register(TentaworksIds.RIG_EVENT, RigEventTrigger::new);

    private TentaworksTriggers() {
    }

    public static void register(IEventBus modBus) {
        REGISTER.register(modBus);
    }
}
