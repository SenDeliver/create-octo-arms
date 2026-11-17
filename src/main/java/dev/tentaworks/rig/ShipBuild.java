package dev.tentaworks.rig;

import dev.tentaworks.compat.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The build of a held Ship (CONTEXT: Ship Grab): the held Ship and every Ship whose world box comes within {@value #TOUCH} of
 * one already in it, step by step, at most {@value #MAX} Ships. Sable keeps no joint graph we could walk, so touching stands in
 * for joined: a mech of jointed parts is one build, a separate deck the wearer stands on is not (until the held Ship touches it).
 * Parts linked only by a rope or a long joint, not touching, are not found. Boxes are measured fresh ({@link #worldBox}: the
 * Ship's own block bounds under this tick's pose), since Sable's world box lags a tick or two; Sable's box only finds the
 * candidates, {@value #BROAD} wider.
 */
public final class ShipBuild {
    public static final double TOUCH = 0.5;
    public static final int MAX = 16;
    static final double BROAD = 4.0;

    private ShipBuild() {
    }

    /** The world box around {@code local} (plot space) under {@code pose}: the 8 corners turned and moved. */
    public static AABB worldBox(Pose pose, AABB local) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vec3 c = pose.toWorld(new Vec3((i & 1) == 0 ? local.minX : local.maxX, (i & 2) == 0 ? local.minY : local.maxY,
                    (i & 4) == 0 ? local.minZ : local.maxZ));
            minX = Math.min(minX, c.x);
            minY = Math.min(minY, c.y);
            minZ = Math.min(minZ, c.z);
            maxX = Math.max(maxX, c.x);
            maxY = Math.max(maxY, c.y);
            maxZ = Math.max(maxZ, c.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * The build of {@code seed}, seed first: {@code box} gives a part's world box (null: unknown, it joins nothing), {@code near}
     * the candidates whose (coarse) box meets a world box, {@code id} tells parts apart.
     */
    public static <T> List<T> group(T seed, Function<T, @Nullable AABB> box, Function<AABB, ? extends Iterable<T>> near, Function<T, Object> id) {
        List<T> out = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        ArrayDeque<T> queue = new ArrayDeque<>();
        out.add(seed);
        seen.add(id.apply(seed));
        queue.add(seed);
        while (!queue.isEmpty() && out.size() < MAX) {
            AABB b = box.apply(queue.poll());
            if (b == null) continue;
            AABB touch = b.inflate(TOUCH);
            for (T c : near.apply(b.inflate(TOUCH + BROAD))) {
                if (out.size() >= MAX) break;
                if (seen.contains(id.apply(c))) continue;
                AABB cb = box.apply(c);
                if (cb == null || !cb.intersects(touch)) continue;
                seen.add(id.apply(c));
                out.add(c);
                queue.add(c);
            }
        }
        return out;
    }
}
