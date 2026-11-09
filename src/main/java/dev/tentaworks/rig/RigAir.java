package dev.tentaworks.rig;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.tentaworks.config.TentaworksConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Air use of a worn Rig (design §5). The Air itself is Create's backtank Air on the Rig stack, shared with a diving helmet.
 */
public final class RigAir {
    private static final double STILL = 1.0E-4;
    /** Air the Actuators owe but have not paid yet (whole Air is taken from the Rig, the rest waits), per wearer. */
    private static final Map<Player, Double> ACTUATOR_DEBT = new WeakHashMap<>();

    public enum Use {
        IDLE, MOVE, CLIMB
    }

    private RigAir() {
    }

    /** The worn Rig's stack (chest slot or Back Slot, {@link WornRig}), or EMPTY. */
    public static ItemStack worn(Player player) {
        return WornRig.of(player).stack();
    }

    public static int air(Player player) {
        ItemStack rig = worn(player);
        return rig.isEmpty() ? 0 : BacktankUtil.getAir(rig);
    }

    /** Creative players use no Air and need none (server: its own game mode, never a client claim). */
    public static boolean free(Player player) {
        return player.isCreative();
    }

    public static boolean has(Player player) {
        return free(player) || air(player) > 0;
    }

    public static Use use(Vec3 motion) {
        if (motion.y > STILL) return Use.CLIMB;
        return motion.lengthSqr() > STILL * STILL ? Use.MOVE : Use.IDLE;
    }

    public static double perTick(Use use, ItemStack rig) {
        double ticks = TentaworksConfig.value(switch (use) {
            case IDLE -> TentaworksConfig.AIR_IDLE;
            case MOVE -> TentaworksConfig.AIR_MOVE;
            case CLIMB -> TentaworksConfig.AIR_CLIMB;
        });
        return tierFactor(rig) * thrift(rig) / ticks;
    }

    private static double tierFactor(ItemStack rig) {
        RigTier tier = rig.getItem() instanceof TentacleRigItem item ? item.tier() : RigTier.COPPER;
        return tier.airFactor();
    }

    public static double capacityFactor() {
        return TentaworksConfig.value(TentaworksConfig.AIR_CAPACITY_FACTOR);
    }

    /** Max Air of a Rig whose Backtank of the same Capacity holds {@code backtankMax}. */
    public static int capacity(int backtankMax) {
        return (int) Math.min(Integer.MAX_VALUE, backtankMax * capacityFactor());
    }

    /** {@code backtankMax} (Create's max Air for this stack) scaled for a Rig; other stacks unchanged. */
    public static int maxAir(ItemStack stack, int backtankMax) {
        return stack.getItem() instanceof TentacleRigItem ? capacity(backtankMax) : backtankMax;
    }

    /** Air per accepted Far Use action. */
    public static double farUseCost(ItemStack rig) {
        return actuatorCost(rig, TentaworksConfig.value(TentaworksConfig.AIR_FAR_USE));
    }

    /** Air per tick while an Actuator holds a Grab. */
    public static double grabPerTick(ItemStack rig) {
        return actuatorCost(rig, 1.0 / TentaworksConfig.value(TentaworksConfig.AIR_GRAB));
    }

    /** Air per Throw. */
    public static double throwCost(ItemStack rig) {
        return actuatorCost(rig, TentaworksConfig.value(TentaworksConfig.AIR_THROW));
    }

    /** Actuator Air use: Copper {@code air}, times the tier factor and Thrift as for moves; none with Deft. */
    static double actuatorCost(ItemStack rig, double air) {
        return RigEnchants.deft(rig) ? 0 : air * tierFactor(rig) * thrift(rig);
    }

    /** Takes {@code air} (an Actuator cost, may be a fraction) from the worn Rig. Creative players pay nothing. */
    public static void pay(ServerPlayer player, double air) {
        if (air <= 0 || free(player)) return;
        ItemStack rig = worn(player);
        if (rig.isEmpty()) return;
        double debt = ACTUATOR_DEBT.getOrDefault(player, 0.0) + air;
        int n = (int) debt;
        if (n > 0) BacktankUtil.consumeAir(player, rig, n);
        ACTUATOR_DEBT.put(player, debt - n);
    }

    static double thrift(ItemStack rig) {
        return RigEnchants.thrift(rig);
    }

    public static double crawlStep() {
        return TentaworksConfig.value(TentaworksConfig.CRAWL_SPEED) / 20.0;
    }
}
