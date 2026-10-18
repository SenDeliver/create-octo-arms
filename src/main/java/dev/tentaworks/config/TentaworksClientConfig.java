package dev.tentaworks.config;

import dev.tentaworks.TentaworksIds;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

import java.util.Locale;

/**
 * Client config ({@code config/tentaworks-client.toml}): looks and controls only, nothing the server needs to know. Edited in
 * the file or from Mods > Octo Arms > Config.
 */
public final class TentaworksClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue WALK_WHILE_HELD;
    public static final ModConfigSpec.EnumValue<ForwardMode> FORWARD;

    /** What {@code W}/{@code S} follow while the Rig carries you. */
    public enum ForwardMode implements TranslatableEnum {
        /** The camera, pitch included: look down and {@code W} takes you down (the default, as the Rig always moved). */
        LOOK,
        /** Where you face on the level only, as walking or creative flight: height changes only with jump and sneak. */
        FLAT;

        @Override
        public Component getTranslatedName() {
            return Component.translatable(TentaworksIds.MOD_ID + ".configuration.movement.forward." + name().toLowerCase(Locale.ROOT));
        }
    }

    static {
        String t = TentaworksIds.MOD_ID + ".configuration.";
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.translation(t + "animation").push("animation");
        WALK_WHILE_HELD = b.comment("Vanilla walk animation (swinging arms and legs) while the Rig carries you or a Ship carries you with it. Off: the body hangs still")
                .translation(t + "animation.walkWhileHeld").define("walkWhileHeld", false);
        b.pop();
        b.translation(t + "movement").push("movement");
        FORWARD = b.comment("What W and S follow while the Rig carries you. LOOK: the camera, look down and W takes you down. FLAT: level only, like walking or creative flight; jump and sneak change the height")
                .translation(t + "movement.forward").defineEnum("forward", ForwardMode.LOOK);
        b.pop();
        SPEC = b.build();
    }

    private TentaworksClientConfig() {
    }

    public static boolean walkWhileHeld() {
        return TentaworksConfig.value(WALK_WHILE_HELD);
    }

    public static ForwardMode forward() {
        return TentaworksConfig.value(FORWARD);
    }
}
