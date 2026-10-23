package dev.tentaworks.dev.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.client.RigClient;
import dev.tentaworks.client.RigVisuals;
import dev.tentaworks.client.TentacleRenderer;
import dev.tentaworks.rig.Grip;
import dev.tentaworks.rig.TentacleGait;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;

/**
 * Dev-only Rig render/sound probes: GAIT lines (own gait per tick, Grip points in world coords), CPERF (Tentacle draw cost per frame),
 * CSOUND (Rig sounds that reached the sound engine, muted or not; plants each visual saw), CSOUNDAT (each whir, hiss and piston sound, with gait trace on).
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class DevRigStats {
    private static final Logger LOG = LoggerFactory.getLogger("tentaworks-script");
    private static final Map<String, Integer> HEARD = new TreeMap<>();
    static boolean gaitTrace;
    private static int frames;
    private static long maxFrameNanos;
    private static long frameNanos;
    private static long inputStart;
    private static int inputTicks;
    private static long inputNanos;
    private static long maxInputNanos;

    private DevRigStats() {
    }

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        if (event.getSound() == null) return;
        String id = event.getSound().getLocation().toString();
        if (id.equals("create:slime_added") || id.equals("create:steam") || id.equals("create:cogs") || id.startsWith("minecraft:block.piston.")) {
            HEARD.merge(id, 1, Integer::sum);
            Minecraft mc = Minecraft.getInstance();
            if (gaitTrace && mc.level != null && !id.equals("create:slime_added"))
                LOG.info("CSOUNDAT gt={} id={} at={}", mc.level.getGameTime(), id, String.format("%.2f,%.2f,%.2f", event.getSound().getX(), event.getSound().getY(), event.getSound().getZ()));
        }
    }

    @SubscribeEvent
    public static void onFrame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        frames++;
        long[] n = new long[1];
        TentacleRenderer.stats((ns, calls) -> n[0] = ns);
        frameNanos += n[0];
        maxFrameNanos = Math.max(maxFrameNanos, n[0]);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInputStart(MovementInputUpdateEvent event) {
        inputStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInputEnd(MovementInputUpdateEvent event) {
        if (RigClient.state() == dev.tentaworks.rig.RigState.STOWED) return;
        long n = System.nanoTime() - inputStart;
        inputTicks++;
        inputNanos += n;
        maxInputNanos = Math.max(maxInputNanos, n);
    }

    static void input(String tag) {
        LOG.info("CINPUT {} ticks={} avg_us={} max_us={} reach={}", tag, inputTicks, inputTicks == 0 ? 0 : String.format("%.1f", inputNanos / 1000.0 / inputTicks),
                String.format("%.1f", maxInputNanos / 1000.0), Minecraft.getInstance().player == null ? 0 : String.format("%.2f", dev.tentaworks.rig.TentacleRigItem.reach(Minecraft.getInstance().player)));
        inputTicks = 0;
        inputNanos = 0;
        maxInputNanos = 0;
    }

    static void perf(String tag) {
        LOG.info("CPERF {} frames={} avg_us_per_frame={} max_us_per_frame={} fps={}", tag, frames,
                frames == 0 ? 0 : String.format("%.1f", frameNanos / 1000.0 / frames), String.format("%.1f", maxFrameNanos / 1000.0), Minecraft.getInstance().getFps());
        frames = 0;
        frameNanos = 0;
        maxFrameNanos = 0;
    }

    static void sounds(String tag) {
        Minecraft mc = Minecraft.getInstance();
        LOG.info("CSOUND {} heard={}", tag, HEARD);
        for (var e : RigVisuals.all()) {
            Entity en = mc.level == null ? null : mc.level.getEntity(e.getIntKey());
            RigVisuals.Visual v = e.getValue();
            LOG.info("CSOUND {} rig={} plants={} plant_sounds={} lifts={}", tag, en == null ? e.getIntKey() : en.getName().getString(), v.plants(), v.plantSounds(), v.lifts());
        }
        TentacleGait g = RigClient.gait();
        int[] t = RigVisuals.plantTotals();
        LOG.info("CSOUND {} own_gait plants={} steps={} own_plants_near={} own_plant_sounds={} other_plants_near={} other_plant_sounds={}",
                tag, g.plants(), g.steps(), t[0], t[1], t[2], t[3]);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!gaitTrace || mc.player == null || mc.level == null) return;
        TentacleGait g = RigClient.gait();
        StringBuilder sb = new StringBuilder();
        int planted = 0;
        double minPair = Double.NaN;
        Vec3[] at = new Vec3[4];
        for (int i = 0; i < 4; i++) at[i] = g.grip(i) == null ? null : g.grip(i).world(mc.level);
        for (int i = 0; i < 4; i++) {
            Grip gr = g.grip(i);
            if (gr != null && g.phase(i) >= 1) planted++;
            sb.append(i == 0 ? "" : ";").append(gr == null ? "-" : String.format("%.2f,%.2f,%.2f@%s/%.2f", at[i].x, at[i].y, at[i].z, gr.face().getName().charAt(0), g.phase(i)));
            for (int j = i + 1; j < 4; j++) {
                if (at[i] == null || at[j] == null) continue;
                double d = at[i].distanceTo(at[j]);
                if (Double.isNaN(minPair) || d < minPair) minPair = d;
            }
        }
        LOG.info("GAIT gt={} state={} pos={} planted={} stepping={} minpair={} slots={}", mc.level.getGameTime(), RigClient.state(),
                String.format("%.3f,%.3f,%.3f", mc.player.getX(), mc.player.getY(), mc.player.getZ()), planted, g.stepping(),
                Double.isNaN(minPair) ? "-" : String.format("%.3f", minPair), sb);
    }
}
