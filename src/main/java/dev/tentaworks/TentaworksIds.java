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
    public static final String SILENT_STEP = "silent_step";
    public static final String RIG_ENCHANTABLE = "enchantable/tentacle_rig";
    public static final String RIG_AIR_SOURCE = "rig_air";
    public static final String ACTUATOR = "actuator";
    public static final String INCOMPLETE_ACTUATOR = "incomplete_actuator";
    public static final String ACTUATORS = "actuators";
    public static final String DEFT = "deft";
    public static final String BOSSES = "bosses";
    public static final String PINNED = "pinned";
    public static final String THROWN = "thrown";
    public static final String FITTING = "fitting";
    public static final String RIG_EVENT = "rig_event";
    public static final String IMPACT = "impact";
    public static final String FITTED = "fitted";
    public static final String FAR_USE_REACH = "far_use_reach";
    public static final String FAR_USE_STRIKE = "far_use_strike";
    public static final String ACTUATOR_HEAVY = "actuator_heavy";

    private TentaworksIds() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
