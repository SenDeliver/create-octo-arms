package dev.tentaworks.compat;

import net.minecraft.world.phys.Vec3;

/**
 * One Ship as seen at one moment: its id, this tick's pose and last tick's. Ship blocks sit at plot ("local") coordinates
 * of the same Level; the pose maps them into the world. Equal when the id is equal. Only unit-scale Ships take Grips.
 */
public final class Frame {
    /** Client render pose source; on the server it is the logical pose. */
    @FunctionalInterface
    public interface RenderPoses {
        Pose at(float partialTick);
    }

    private final Object id;
    private final Pose pose;
    private final Pose last;
    private final boolean unitScale;
    private final boolean client;
    private final RenderPoses render;

    public Frame(Object id, Pose pose, Pose last, boolean unitScale, boolean client, RenderPoses render) {
        this.id = id;
        this.pose = pose;
        this.last = last;
        this.unitScale = unitScale;
        this.client = client;
        this.render = render;
    }

    public Object id() {
        return id;
    }

    public Pose pose() {
        return pose;
    }

    public Pose last() {
        return last;
    }

    public boolean unitScale() {
        return unitScale;
    }

    public boolean client() {
        return client;
    }

    public Vec3 toLocal(Vec3 world) {
        return pose.toLocal(world);
    }

    public Vec3 toWorld(Vec3 local) {
        return pose.toWorld(local);
    }

    public Vec3 normalToLocal(Vec3 n) {
        return pose.normalToLocal(n);
    }

    public Vec3 normalToWorld(Vec3 n) {
        return pose.normalToWorld(n);
    }

    /** This tick's move of a world point riding the Ship: {@code pose · last⁻¹ · p − p}. */
    public Vec3 carry(Vec3 world) {
        return pose.carrySince(last, world);
    }

    /** Interpolated client pose for rendering (no client classes involved). */
    public Pose renderPose(float partialTick) {
        return render == null ? pose : render.at(partialTick);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Frame f && id.equals(f.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
