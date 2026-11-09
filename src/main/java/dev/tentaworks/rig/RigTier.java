package dev.tentaworks.rig;

import dev.tentaworks.config.TentaworksConfig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;

public enum RigTier {
    COPPER(TentaworksConfig.REACH_COPPER, null, TentaworksConfig.GRAB_THROW_SPEED_COPPER, TentaworksConfig.GRAB_MASS_CAP_COPPER),
    NETHERITE(TentaworksConfig.REACH_NETHERITE, TentaworksConfig.AIR_NETHERITE_FACTOR, TentaworksConfig.GRAB_THROW_SPEED_NETHERITE,
            TentaworksConfig.GRAB_MASS_CAP_NETHERITE);

    private final ModConfigSpec.DoubleValue reach;
    private final ModConfigSpec.DoubleValue airFactor;
    private final ModConfigSpec.DoubleValue throwSpeed;
    private final ModConfigSpec.DoubleValue massCap;

    RigTier(ModConfigSpec.DoubleValue reach, ModConfigSpec.DoubleValue airFactor, ModConfigSpec.DoubleValue throwSpeed, ModConfigSpec.DoubleValue massCap) {
        this.reach = reach;
        this.airFactor = airFactor;
        this.throwSpeed = throwSpeed;
        this.massCap = massCap;
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

    /** Throw speed, blocks per tick. */
    public double throwSpeed() {
        return TentaworksConfig.value(throwSpeed);
    }

    /** Heaviest Ship this tier can Grab, Sable mass. */
    public double massCap() {
        return TentaworksConfig.value(massCap);
    }
}
