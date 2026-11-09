package dev.tentaworks.rig;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A Thrown entity (attachment {@code tentaworks:thrown}): who threw it, from where, its speed last tick (blocks per tick), and
 * whether its Impact is still to come.
 */
public record Thrown(UUID thrower, Vec3 start, double lastSpeed, boolean armed) {
    public Thrown withSpeed(double speed) {
        return new Thrown(thrower, start, speed, armed);
    }

    public Thrown disarmed() {
        return new Thrown(thrower, start, lastSpeed, false);
    }
}
