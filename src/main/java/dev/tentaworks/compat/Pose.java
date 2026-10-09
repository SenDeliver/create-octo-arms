package dev.tentaworks.compat;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * A rigid Ship pose: {@code world = position + orientation · (local − pivot)}. An immutable copy, never Sable's live object.
 */
public final class Pose {
    public static final Pose IDENTITY = new Pose(new Vector3d(), new Quaterniond(), new Vector3d());

    private final Vector3d position;
    private final Quaterniond orientation;
    private final Vector3d pivot;

    public Pose(Vector3dc position, Quaterniondc orientation, Vector3dc pivot) {
        this.position = new Vector3d(position);
        this.orientation = new Quaterniond(orientation).normalize();
        this.pivot = new Vector3d(pivot);
    }

    public Vec3 toWorld(Vec3 local) {
        Vector3d v = orientation.transform(new Vector3d(local.x - pivot.x, local.y - pivot.y, local.z - pivot.z));
        return new Vec3(v.x + position.x, v.y + position.y, v.z + position.z);
    }

    public Vec3 toLocal(Vec3 world) {
        Vector3d v = orientation.transformInverse(new Vector3d(world.x - position.x, world.y - position.y, world.z - position.z));
        return new Vec3(v.x + pivot.x, v.y + pivot.y, v.z + pivot.z);
    }

    public Vec3 normalToWorld(Vec3 n) {
        Vector3d v = orientation.transform(new Vector3d(n.x, n.y, n.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public Vec3 normalToLocal(Vec3 n) {
        Vector3d v = orientation.transformInverse(new Vector3d(n.x, n.y, n.z));
        return new Vec3(v.x, v.y, v.z);
    }

    /** Where a world point riding the Ship at pose {@code then} is now, minus where it was. */
    public Vec3 carrySince(Pose then, Vec3 world) {
        return toWorld(then.toLocal(world)).subtract(world);
    }

    /** Local → world as a matrix, written into {@code dest}. */
    public Matrix4d matrix(Matrix4d dest) {
        return dest.translation(position).rotate(orientation).translate(-pivot.x, -pivot.y, -pivot.z);
    }

    public Vector3dc position() {
        return position;
    }

    public Quaterniondc orientation() {
        return orientation;
    }

    public Vector3dc pivot() {
        return pivot;
    }
}
