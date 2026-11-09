package dev.tentaworks.client;

import dev.tentaworks.TentaworksIds;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Throw and Impact feedback: the thrower's camera kicks a little, the arms follow through, and the thrown mob is watched until
 * it slams into something (its speed drops by more than 60% in a tick), where an Impact sparks and clanks.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class ActuatorFx {
    private static final float KICK_DEGREES = 2.5F, KICK_TICKS = 5;
    /** Slowest flight that can make an Impact, blocks per tick, and the most ticks a thrown mob is watched. */
    private static final double MIN_SPEED = 0.3;
    private static final int WATCH_TICKS = 60;
    private static final Int2ObjectMap<Flight> FLIGHTS = new Int2ObjectOpenHashMap<>();
    private static int kickTick = Integer.MIN_VALUE / 2;

    private static final class Flight {
        Vec3 last;
        double speed;
        int age;

        Flight(Vec3 at) {
            last = at;
        }
    }

    private ActuatorFx() {
    }

    /** {@code wearer} threw {@code thrownId} (-1 a Ship or unknown) along {@code dir}. */
    static void thrown(Player wearer, int thrownId, Vec3 dir) {
        ActuatorRenderer.thrown(wearer, dir);
        Minecraft mc = Minecraft.getInstance();
        if (wearer == mc.player) kickTick = wearer.tickCount;
        Entity e = thrownId >= 0 ? wearer.level().getEntity(thrownId) : null;
        if (e != null) FLIGHTS.put(thrownId, new Flight(e.position()));
    }

    /** An Impact at {@code at} for a mob flying at {@code speed} blocks per tick: sparks, dust and a metal clank. */
    static void impact(Vec3 at, double speed) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource r = level.random;
        int sparks = Mth.clamp((int) (speed * 12), 4, 16);
        for (int i = 0; i < sparks; i++) {
            Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian() + 0.4, r.nextGaussian()).scale(0.25);
            level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 4; i++)
            level.addParticle(ParticleTypes.POOF, at.x + r.nextGaussian() * 0.2, at.y + r.nextGaussian() * 0.2, at.z + r.nextGaussian() * 0.2, 0, 0.02, 0);
        float volume = (float) Mth.clamp(0.25 + speed * 0.3, 0.25, 0.7);
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, volume, 1.5F + r.nextFloat() * 0.2F, false);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            FLIGHTS.clear();
            return;
        }
        if (FLIGHTS.isEmpty() || mc.isPaused()) return;
        var it = FLIGHTS.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Flight f = en.getValue();
            Entity e = mc.level.getEntity(en.getIntKey());
            if (e == null || e.isRemoved() || ++f.age > WATCH_TICKS) {
                it.remove();
                continue;
            }
            Vec3 at = e.position();
            double speed = at.distanceTo(f.last);
            f.last = at;
            if (f.speed >= MIN_SPEED && speed < f.speed * 0.4) {
                impact(e.getBoundingBox().getCenter(), f.speed);
                it.remove();
            } else {
                f.speed = speed;
            }
        }
    }

    /** One Impact per Throw: a server Impact signal for a watched mob ends its watch. */
    static void impacted(int thrownId) {
        FLIGHTS.remove(thrownId);
    }

    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float age = mc.player.tickCount - kickTick + (float) event.getPartialTick();
        if (age < 0 || age >= KICK_TICKS) return;
        float k = 1 - age / KICK_TICKS;
        event.setPitch(event.getPitch() - KICK_DEGREES * k * k);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FLIGHTS.clear();
    }
}
