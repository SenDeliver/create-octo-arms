package dev.tentaworks.rig;

import dev.tentaworks.config.TentaworksConfig;
import dev.tentaworks.network.ActuatorFxPacket;
import dev.tentaworks.registry.TentaworksAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Impact of a Thrown entity (CONTEXT: Impact), server side. Each tick after the Throw: the first time it slams into a block
 * (a collision, at the speed it had into that surface the tick before) or touches a living entity (at its full speed), Impact
 * damage ({@link #damage}) goes once to it and to every living entity its box touches, as the thrower's own damage; a player
 * only where the thrower may harm them (PvP), and its trackers hear of it ({@link ActuatorFxPacket} IMPACT) when it was at least
 * {@code impact.minSpeed} fast. It stays Thrown until it comes to rest (on the ground or in a liquid and nearly
 * still, or after {@value #MAX_TICKS} ticks), then {@value RigEvents#THROW_DISTANCE} fires with the horizontal distance from
 * where it was thrown.
 */
public final class Impact {
    static final int MAX_TICKS = 200;
    private static final double REST_SPEED = 0.05;
    private static final double TOUCH = 0.1;
    private static final Map<Entity, Flight> FLYING = new IdentityHashMap<>();

    private static final class Flight {
        int age;
        Vec3 last;

        Flight(Vec3 velocity) {
            last = velocity;
        }
    }

    private Impact() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> FLYING.clear());
    }

    /** {@code entity} was just flung with {@code velocity} by {@code thrower}: its Impact is armed. */
    public static void thrown(ServerPlayer thrower, Entity entity, Vec3 velocity) {
        entity.setData(TentaworksAttachments.THROWN, new Thrown(thrower.getUUID(), entity.position(), velocity.length(), true));
        FLYING.put(entity, new Flight(velocity));
    }

    /** Impact damage at {@code speed} (blocks per tick): (speed − min) × per speed, between 0 and {@code cap}. */
    public static float damage(double speed, double minSpeed, double perSpeed, double cap) {
        return (float) Mth.clamp((speed - minSpeed) * perSpeed, 0, cap);
    }

    public static float damage(double speed) {
        return damage(speed, TentaworksConfig.value(TentaworksConfig.IMPACT_MIN_SPEED), TentaworksConfig.value(TentaworksConfig.IMPACT_DAMAGE_PER_SPEED),
                TentaworksConfig.value(TentaworksConfig.IMPACT_CAP));
    }

    /** The speed a collision this tick hit with: the part of last tick's velocity into the surfaces it met. */
    public static double intoSurface(Vec3 last, boolean horizontal, boolean vertical) {
        double h = horizontal ? last.horizontalDistanceSqr() : 0, v = vertical ? last.y * last.y : 0;
        return Math.sqrt(h + v);
    }

    private static void tick(MinecraftServer server) {
        if (FLYING.isEmpty()) return;
        for (Map.Entry<Entity, Flight> en : new ArrayList<>(FLYING.entrySet())) {
            Entity e = en.getKey();
            Flight f = en.getValue();
            Thrown t = e.getExistingDataOrNull(TentaworksAttachments.THROWN);
            if (t == null) {
                FLYING.remove(e);
                continue;
            }
            ServerPlayer thrower = server.getPlayerList().getPlayer(t.thrower());
            if (e.isRemoved() || !e.isAlive()) {
                land(e, t, thrower);
                continue;
            }
            f.age++;
            Vec3 v = e.getDeltaMovement();
            if (t.armed()) {
                List<LivingEntity> touched = e.level().getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(TOUCH),
                        o -> o != e && o != thrower && o.isAlive() && !o.isSpectator());
                double speed = !touched.isEmpty() ? f.last.length() : intoSurface(f.last, e.horizontalCollision, e.verticalCollision);
                if (!touched.isEmpty() || e.horizontalCollision || e.verticalCollision) {
                    hit(e, thrower, touched, damage(speed));
                    if (speed >= TentaworksConfig.value(TentaworksConfig.IMPACT_MIN_SPEED))
                        PacketDistributor.sendToPlayersTrackingEntity(e, ActuatorFxPacket.impact(e.getId(), e.getBoundingBox().getCenter(), speed));
                    t = t.disarmed();
                }
            }
            f.last = v;
            if (f.age > MAX_TICKS || (e.onGround() || e.isInLiquid()) && v.horizontalDistance() < REST_SPEED) {
                land(e, t, thrower);
                continue;
            }
            e.setData(TentaworksAttachments.THROWN, t.withSpeed(v.length()));
        }
    }

    private static void hit(Entity e, @Nullable ServerPlayer thrower, List<LivingEntity> touched, float damage) {
        if (thrower != null) RigEvents.fire(thrower, RigEvents.IMPACT, damage);
        if (damage <= 0) return;
        if (e instanceof LivingEntity self) self.hurt(ImpactDamage.source(e.level(), null, thrower), damage);
        for (LivingEntity o : touched) {
            if (o instanceof Player p && (thrower == null || !thrower.canHarmPlayer(p))) continue;
            o.hurt(ImpactDamage.source(e.level(), e, thrower), damage);
        }
    }

    private static void land(Entity e, Thrown t, @Nullable ServerPlayer thrower) {
        FLYING.remove(e);
        e.removeData(TentaworksAttachments.THROWN);
        if (thrower != null) RigEvents.fire(thrower, RigEvents.THROW_DISTANCE, e.position().subtract(t.start()).horizontalDistance());
    }
}
