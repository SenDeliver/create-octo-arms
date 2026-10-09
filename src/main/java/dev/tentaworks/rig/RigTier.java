package dev.tentaworks.rig;

import dev.tentaworks.config.TentaworksConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;

public enum RigTier {
    COPPER(TentaworksConfig.REACH_COPPER, null),
    NETHERITE(TentaworksConfig.REACH_NETHERITE, TentaworksConfig.AIR_NETHERITE_FACTOR);

    private final ModConfigSpec.DoubleValue reach;
    private final ModConfigSpec.DoubleValue airFactor;

    RigTier(ModConfigSpec.DoubleValue reach, ModConfigSpec.DoubleValue airFactor) {
        this.reach = reach;
        this.airFactor = airFactor;
    }

    public double reach() {
        return TentaworksConfig.value(reach);
    }

    /** The anvil repair item: the tier's armor material (Create's copper armor, vanilla netherite). */
    public Item repairItem() {
        return this == COPPER ? Items.COPPER_INGOT : Items.NETHERITE_INGOT;
    }

    public double airFactor() {
        return airFactor == null ? 1.0 : TentaworksConfig.value(airFactor);
    }
}
