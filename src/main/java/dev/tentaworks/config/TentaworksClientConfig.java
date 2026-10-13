package dev.tentaworks.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config ({@code config/tentaworks-client.toml}): looks only, nothing the server needs to know. */
public final class TentaworksClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue WALK_WHILE_HELD;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("animation");
        WALK_WHILE_HELD = b.comment("Vanilla walk animation (swinging arms and legs) while the Rig carries you or a Ship carries you with it. Off: the body hangs still")
                .define("walkWhileHeld", false);
        b.pop();
        SPEC = b.build();
    }

    private TentaworksClientConfig() {
    }

    public static boolean walkWhileHeld() {
        return TentaworksConfig.value(WALK_WHILE_HELD);
    }
}
