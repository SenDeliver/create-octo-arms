package dev.tentaworks.rig;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Pickup while the Rig carries the wearer: vanilla's pickup box reaches half a block below the feet, and a carried wearer
 * floats at Standing Height, so items and XP on the floor under them would lie out of reach. The strip from vanilla's box
 * down to the floor (one ray down from the feet, at most Standing Height) and the same half block below it is touched as
 * vanilla touches its own box, so pickup events, pickup delay and owners still apply; a floor of a room below is never reached.
 */
public final class RigPickup {
    static final double SLACK = 0.5;
    private static final double EPS = 1.0E-3;

    private RigPickup() {
    }

    /** Vanilla's pickup box of a wearer not on a Ride. */
    public static AABB vanilla(Player player) {
        return player.getBoundingBox().inflate(1, SLACK, 1);
    }

    /** How far below the {@code feet} the floor lies: at {@code floor} (NaN: none within {@code limit}), clamped to {@code limit}. */
    static double below(double feet, double floor, double limit) {
        return Double.isNaN(floor) ? limit : Mth.clamp(feet - floor, 0, limit);
    }

    /** The strip under {@code vanilla} down to {@value #SLACK} below the floor, {@code below} under the {@code feet}; null when vanilla's box already reaches it. */
    static @Nullable AABB strip(AABB vanilla, double feet, double below) {
        if (below <= EPS) return null;
        return new AABB(vanilla.minX, feet - below - SLACK, vanilla.minZ, vanilla.maxX, vanilla.minY, vanilla.maxZ);
    }

    static void tick(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()) return;
        Vec3 feet = player.position();
        double limit = RigSettings.of(WornRig.of(player).stack()).standingHeight();
        BlockHitResult hit = player.level().clip(new ClipContext(feet, feet.subtract(0, limit, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        AABB box = strip(vanilla(player), feet.y, below(feet.y, hit.getType() == HitResult.Type.MISS ? Double.NaN : hit.getLocation().y, limit));
        if (box == null) return;
        for (Entity e : player.level().getEntities(player, box, e -> e instanceof ItemEntity || e instanceof ExperienceOrb))
            if (!e.isRemoved()) e.playerTouch(player);
    }
}
