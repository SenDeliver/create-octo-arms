package dev.tentaworks.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TentaworksConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue REACH_COPPER;
    public static final ModConfigSpec.DoubleValue REACH_NETHERITE;
    public static final ModConfigSpec.DoubleValue SPEED_MAX;
    public static final ModConfigSpec.DoubleValue AIR_IDLE;
    public static final ModConfigSpec.DoubleValue AIR_MOVE;
    public static final ModConfigSpec.DoubleValue AIR_CLIMB;
    public static final ModConfigSpec.DoubleValue AIR_NETHERITE_FACTOR;
    public static final ModConfigSpec.DoubleValue CRAWL_SPEED;
    public static final ModConfigSpec.DoubleValue ENCHANT_FAR_REACH;
    public static final ModConfigSpec.DoubleValue ENCHANT_THRIFT;
    public static final ModConfigSpec.DoubleValue ENCHANT_SWIFT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("reach");
        REACH_COPPER = b.comment("Grip Reach of a Copper Rig, blocks. Far Reach adds to it, up to 16 in total").defineInRange("copper", 4.0, 1.0, 16.0);
        REACH_NETHERITE = b.comment("Grip Reach of a Netherite Rig, blocks. Far Reach adds to it, up to 16 in total").defineInRange("netherite", 7.0, 1.0, 16.0);
        b.pop();
        b.push("speed");
        SPEED_MAX = b.comment("Top Rig speed, blocks per second. Caps the Speed in every player's Rig Settings; Swift raises both on its Rig").defineInRange("max", 8.0, 1.0, 20.0);
        b.pop();
        b.push("air");
        AIR_IDLE = b.comment("Copper Rig held still (Idle, Hang): ticks per 1 Air").defineInRange("idle", 80.0, 1.0, 72000.0);
        AIR_MOVE = b.comment("Copper Rig moving level or down: ticks per 1 Air").defineInRange("move", 20.0, 1.0, 72000.0);
        AIR_CLIMB = b.comment("Copper Rig climbing (net +Y): ticks per 1 Air").defineInRange("climb", 10.0, 1.0, 72000.0);
        AIR_NETHERITE_FACTOR = b.comment("Air use of a Netherite Rig relative to Copper").defineInRange("netheriteFactor", 0.5, 0.0, 4.0);
        b.pop();
        b.push("crawl");
        CRAWL_SPEED = b.comment("Descent speed with no Air left (Crawl), blocks per second").defineInRange("speed", 1.0, 0.1, 8.0);
        b.pop();
        b.push("enchant");
        ENCHANT_FAR_REACH = b.comment("Far Reach: extra Reach per level, blocks").defineInRange("farReachPerLevel", 1.0, 0.0, 8.0);
        ENCHANT_THRIFT = b.comment("Thrift: Air use cut per level (0.15 = -15 %). Level III stays above zero").defineInRange("thriftPerLevel", 0.15, 0.0, 0.3);
        ENCHANT_SWIFT = b.comment("Swift: extra Speed per level (0.15 = +15 %)").defineInRange("swiftPerLevel", 0.15, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private TentaworksConfig() {
    }

    public static double value(ModConfigSpec.DoubleValue v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }
}
