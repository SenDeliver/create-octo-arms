package dev.tentaworks;

import net.minecraft.resources.ResourceLocation;

public final class TentaworksIds {
    public static final String MOD_ID = "tentaworks";

    public static final String COPPER_TENTACLE_RIG = "copper_tentacle_rig";
    public static final String NETHERITE_TENTACLE_RIG = "netherite_tentacle_rig";
    public static final String PLACEABLE_SUFFIX = "_placeable";
    public static final String TENTACLE = "tentacle";
    public static final String INCOMPLETE_TENTACLE = "incomplete_tentacle";
    public static final String RIG_SETTINGS = "rig_settings";
    public static final String FAR_REACH = "far_reach";
    public static final String THRIFT = "thrift";
    public static final String SWIFT = "swift";
    public static final String RIG_ENCHANTABLE = "enchantable/tentacle_rig";
    public static final String RIG_AIR_SOURCE = "rig_air";

    private TentaworksIds() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
