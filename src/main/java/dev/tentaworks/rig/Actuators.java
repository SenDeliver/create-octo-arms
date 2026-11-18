package dev.tentaworks.rig;

import dev.tentaworks.registry.TentaworksDataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Actuators of a Rig (component {@code tentaworks:actuators}, adr/0007): present means fitted. They work Stowed or Active,
 * wherever the wearer's hands work, from the Rig's Air; limp when it has none. Furled ({@code tentaworks:actuators_furled}) they
 * stay fitted but are off: no Grab, no Far Use, no carried item, no Air.
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

    /** Fitted and Furled: the wearer turned the Actuators off. */
    public static boolean furled(ItemStack rig) {
        return fitted(rig) && rig.has(TentaworksDataComponents.ACTUATORS_FURLED.get());
    }

    public static boolean furled(LivingEntity entity) {
        return furled(WornRig.of(entity).stack());
    }

    /** Fitted and not Furled: the Actuators are in use (limp or not). */
    public static boolean on(ItemStack rig) {
        return fitted(rig) && !rig.has(TentaworksDataComponents.ACTUATORS_FURLED.get());
    }

    public static boolean on(LivingEntity entity) {
        return on(WornRig.of(entity).stack());
    }

    /** Furls ({@code furled}) or unfurls the Actuators of {@code rig} in place; false when it has none. */
    public static boolean furl(ItemStack rig, boolean furled) {
        if (!fitted(rig)) return false;
        if (furled) rig.set(TentaworksDataComponents.ACTUATORS_FURLED.get(), Unit.INSTANCE);
        else rig.remove(TentaworksDataComponents.ACTUATORS_FURLED.get());
        return true;
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

    /** {@link #on}, but the Rig has no Air left (creative players never run out): the Actuators hang limp and act on nothing. */
    public static boolean limp(Player player) {
        return on(player) && !RigAir.has(player);
    }

    /** {@link #on} and not limp: the Actuators may act. */
    public static boolean ready(Player player) {
        return on(player) && RigAir.has(player);
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
