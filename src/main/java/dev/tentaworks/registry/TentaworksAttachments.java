package dev.tentaworks.registry;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.Pinned;
import dev.tentaworks.rig.Thrown;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Entity attachments of the Actuators. Neither has a serializer, so neither ever reaches NBT: a held or Thrown mob saved by a
 * chunk unload or server stop loads as itself. Read them with {@code getExistingData}; there is no default value.
 */
public final class TentaworksAttachments {
    private static final DeferredRegister<AttachmentType<?>> REGISTER = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, TentaworksIds.MOD_ID);

    /** On a mob an Actuator holds, or one just set down and still calm. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Pinned>> PINNED = REGISTER.register(TentaworksIds.PINNED,
            () -> AttachmentType.builder(TentaworksAttachments.<Pinned>noDefault(TentaworksIds.PINNED)).build());
    /** On a Thrown entity until its Impact is spent or it stops. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Thrown>> THROWN = REGISTER.register(TentaworksIds.THROWN,
            () -> AttachmentType.builder(TentaworksAttachments.<Thrown>noDefault(TentaworksIds.THROWN)).build());

    private TentaworksAttachments() {
    }

    public static void register(IEventBus modBus) {
        REGISTER.register(modBus);
    }

    private static <T> Supplier<T> noDefault(String id) {
        return () -> {
            throw new IllegalStateException("tentaworks:" + id + " has no default: use getExistingData");
        };
    }
}
