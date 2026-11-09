package dev.tentaworks.rig;

import dev.tentaworks.registry.TentaworksDataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Actuators of a Rig (component {@code tentaworks:actuators}, adr/0007): present means fitted. They work Stowed or Active,
 * wherever the wearer's hands work, from the Rig's Air; limp when it has none.
 */
public final class Actuators {
    private Actuators() {
    }

    public static boolean fitted(ItemStack rig) {
        return rig.getItem() instanceof TentacleRigItem && rig.has(TentaworksDataComponents.ACTUATORS.get());
    }

    /** The Rig the entity wears ({@link WornRig}: chest slot or Back Slot) has Actuators. */
    public static boolean fitted(LivingEntity entity) {
        return fitted(WornRig.of(entity).stack());
    }

    /** A copy of {@code rig} with Actuators fitted; everything else on it stays. */
    public static ItemStack fit(ItemStack rig) {
        ItemStack out = rig.copyWithCount(1);
        out.set(TentaworksDataComponents.ACTUATORS.get(), Unit.INSTANCE);
        return out;
    }

    /** Throw speed of this Rig's tier, blocks per tick, 0 for anything else. */
    public static double throwSpeed(ItemStack rig) {
        return rig.getItem() instanceof TentacleRigItem item ? item.tier().throwSpeed() : 0;
    }

    /** Fitted, but the Rig has no Air left (creative players never run out): the Actuators hang limp and act on nothing. */
    public static boolean limp(Player player) {
        return fitted(player) && !RigAir.has(player);
    }

    /** Fitted and not limp: the Actuators may act. */
    public static boolean ready(Player player) {
        return fitted(player) && RigAir.has(player);
    }

    public static double farUseCost(Player player) {
        return RigAir.farUseCost(RigAir.worn(player));
    }

    public static double grabPerTick(Player player) {
        return RigAir.grabPerTick(RigAir.worn(player));
    }

    public static double throwCost(Player player) {
        return RigAir.throwCost(RigAir.worn(player));
    }
}
