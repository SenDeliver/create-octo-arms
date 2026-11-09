package dev.tentaworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tentaworks.TentaworksIds;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Rig Settings while worn and the toggle for the key-only Activation Mode are unbound by default (design §7). Grab is V (G is
 * Curios' own key).
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class TentaworksKeys {
    public static final String CATEGORY = "key.categories." + TentaworksIds.MOD_ID;
    public static final KeyMapping SETTINGS = new KeyMapping("key." + TentaworksIds.MOD_ID + ".settings",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, CATEGORY);
    public static final KeyMapping TOGGLE = new KeyMapping("key." + TentaworksIds.MOD_ID + ".toggle",
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, CATEGORY);
    public static final KeyMapping GRAB = new KeyMapping("key." + TentaworksIds.MOD_ID + ".grab",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY);

    private TentaworksKeys() {
    }

    @SubscribeEvent
    static void register(RegisterKeyMappingsEvent event) {
        event.register(SETTINGS);
        event.register(TOGGLE);
        event.register(GRAB);
    }
}
