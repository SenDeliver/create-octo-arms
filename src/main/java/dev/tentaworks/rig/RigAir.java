package dev.tentaworks.rig;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.tentaworks.config.TentaworksConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Air use of a worn Rig (design §5). The Air itself is Create's backtank Air on the Rig stack, shared with a diving helmet.
 */
public final class RigAir {
    private static final double STILL = 1.0E-4;

    public enum Use {
        IDLE, MOVE, CLIMB
    }

    private RigAir() {
    }

    public static ItemStack worn(Player player) {
        ItemStack stack = player.getItemBySlot(TentacleRigItem.SLOT);
        return stack.getItem() instanceof TentacleRigItem ? stack : ItemStack.EMPTY;
    }

    public static float air(Player player) {
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
        RigTier tier = rig.getItem() instanceof TentacleRigItem item ? item.tier() : RigTier.COPPER;
        return tier.airFactor() * thrift(rig) / ticks;
    }

    static double thrift(ItemStack rig) {
        return RigEnchants.thrift(rig);
    }

    public static double crawlStep() {
        return TentaworksConfig.value(TentaworksConfig.CRAWL_SPEED) / 20.0;
    }
}
