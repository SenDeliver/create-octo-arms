package dev.tentaworks.compat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A Ship's rigid body on the server (Ship Grab): its pose, mass and velocity. Valid while {@link #alive()}. */
public interface ShipBody {
    Object id();

    /** The Ship's pose now. */
    Frame frame();

    /** The Ship's blocks in the world now, as one axis-aligned box; null when unknown. Sable updates it a tick or two late. */
    @Nullable AABB worldBounds();

    /** The Ship's blocks in its own (plot) space, the space of {@link Frame#toWorld}; null when unknown. */
    @Nullable AABB localBounds();

    /** Sable mass (stone about 2 per block), -1 when unknown. */
    double mass();

    boolean alive();

    /** Sets the body's velocity: linear in blocks per second, angular in radians per second about world axes. */
    void velocity(Vec3 linear, Vec3 angular);
}
