package dev.tentaworks.dev.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.client.ActuatorHud;
import dev.tentaworks.client.ActuatorRenderer;
import dev.tentaworks.client.ActuatorSight;
import dev.tentaworks.client.ActuatorView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Dev-only tools of the look suite, DevScript lines: burst NAME N (a shot per tick, NAME_00..), guiscale N, spot X Y Z ok|bad and
 * spot off (a set-down spot to show), mainarm left|right, hudstate TAG (CHUD line: what the crosshair HUD shows), looktrace on|off
 * (CLOOK line per tick: what vanilla and the Actuators drew of each hand in first person, the frames with a hand drawn twice,
 * the crosshair mark, the Grab cue and how far the acting Actuator is from its target).
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class DevLook {
    private static final Logger LOG = LoggerFactory.getLogger("tentaworks-script");
    private static boolean trace;
    private static String burst;
    private static int burstLeft, burstCount;
    private static final boolean[] vanilla = new boolean[2];
    private static final int[] before = new int[2], vanillaFrames = new int[2], actuatorFrames = new int[2], doubled = new int[2];
    private static int frames;

    private DevLook() {
    }

    /** Runs a look line: ticks to wait after it (0 none), -1 when the line is not one of these. */
    static int run(Minecraft mc, String[] a) {
        switch (a[0]) {
            case "burst" -> {
                burst = a[1];
                burstCount = 0;
                burstLeft = Integer.parseInt(a[2]);
                mc.getToasts().clear();
                return burstLeft + 1;
            }
            case "guiscale" -> {
                mc.options.guiScale().set(Integer.parseInt(a[1]));
                mc.resizeDisplay();
                return 2;
            }
            case "spot" -> {
                ActuatorView.preview(a[1].equals("off") ? null
                        : new ActuatorView.Spot(new Vec3(Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3])), a[4].equals("ok")));
                return 0;
            }
            case "hudstate" -> {
                ActuatorView.Spot spot = ActuatorView.setDownSpot(mc.player);
                LOG.info("CHUD {} gt={} shown={} mark={} cue={} hold={} spot={} throwkey={}", a[1], mc.level.getGameTime(), ActuatorHud.marksShown(),
                        ActuatorSight.mark(), ActuatorSight.cue(), ActuatorView.holdArm(mc.player), spot == null ? "-" : spot.valid() ? "ok" : "bad",
                        ActuatorView.throwNeedsGrabKey(mc.player));
                return 0;
            }
            case "mainarm" -> {
                mc.options.mainHand().set(a[1].equals("left") ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
                mc.options.broadcastOptions();
                return 2;
            }
            case "looktrace" -> {
                trace = a[1].equals("on");
                return 0;
            }
            default -> {
                return -1;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onHand(RenderHandEvent event) {
        boolean main = event.getHand() == InteractionHand.MAIN_HAND;
        Minecraft mc = Minecraft.getInstance();
        if (!event.getItemStack().isEmpty() || main && mc.player != null && !mc.player.isInvisible()) vanilla[main ? 0 : 1] = true;
    }

    @SubscribeEvent
    static void onFrameStart(RenderFrameEvent.Pre event) {
        for (int h = 0; h < 2; h++) {
            vanilla[h] = false;
            before[h] = ActuatorRenderer.HAND_DRAWS[h];
        }
    }

    @SubscribeEvent
    static void onFrameEnd(RenderFrameEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        frames++;
        for (int h = 0; h < 2; h++) {
            boolean actuator = ActuatorRenderer.HAND_DRAWS[h] != before[h];
            if (vanilla[h]) vanillaFrames[h]++;
            if (actuator) actuatorFrames[h]++;
            if (vanilla[h] && actuator) doubled[h]++;
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (burstLeft > 0) {
            String name = String.format("%s_%02d.png", burst, burstCount++);
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> LOG.info("screenshot {}: {}", name, msg.getString()));
            burstLeft--;
        }
        if (trace) LOG.info("CLOOK gt={} first={} frames={} vhand={},{} ahand={},{} dbl={},{} mark={} cue={} lag={}", mc.level.getGameTime(),
                mc.options.getCameraType().isFirstPerson(), frames, vanillaFrames[0], vanillaFrames[1], actuatorFrames[0], actuatorFrames[1], doubled[0],
                doubled[1], ActuatorSight.mark(), ActuatorSight.cue(), String.format(Locale.ROOT, "%.2f", ActuatorRenderer.lag(mc.player)));
        frames = 0;
        for (int h = 0; h < 2; h++) vanillaFrames[h] = actuatorFrames[h] = doubled[h] = 0;
    }
}
