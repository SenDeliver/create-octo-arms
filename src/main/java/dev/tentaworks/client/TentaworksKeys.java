package dev.tentaworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tentaworks.TentaworksIds;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;

/** Both keys are unbound by default (design §7): Rig Settings while worn, and the toggle for the key-only Activation Mode. */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TentaworksKeys {
    public static final String CATEGORY = "key.categories." + TentaworksIds.MOD_ID;
    public static final KeyMapping SETTINGS = new KeyMapping("key." + TentaworksIds.MOD_ID + ".settings",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, CATEGORY);
    public static final KeyMapping TOGGLE = new KeyMapping("key." + TentaworksIds.MOD_ID + ".toggle",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, CATEGORY);

    private TentaworksKeys() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SETTINGS);
        event.register(TOGGLE);
    }
}
