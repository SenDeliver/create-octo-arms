package dev.tentaworks.rig;

import dev.tentaworks.registry.TentaworksTriggers;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Spider;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * What the server tells the advancements: each event goes to the {@code tentaworks:rig_event} criterion ({@link RigEventTrigger})
 * with its number. {@value #HANG}: whole seconds of one unbroken Hang under a ceiling; {@value #SPIDER_KILL}: the wearer killed a
 * spider (cave spiders too) with the Rig out; {@value #GRAB_BLAZE}: a Grab took a Blaze; {@value #SHIP_GRAB}: how far a Grabbed
 * Ship was set down from where it was taken; {@value #THROW}: a Throw, at its speed; {@value #IMPACT}: an Impact, its damage;
 * {@value #THROW_DISTANCE}: how far (horizontally) a Thrown mob came to rest from where it was thrown.
 */
public final class RigEvents {
    public static final String HANG = "hang";
    public static final String SPIDER_KILL = "spider_kill";
    public static final String GRAB_BLAZE = "grab_blaze";
    public static final String SHIP_GRAB = "ship_grab";
    public static final String THROW = "throw";
    public static final String IMPACT = "impact";
    public static final String THROW_DISTANCE = "throw_distance";
    /** Game time a wearer started Hanging and the last tick seen Hanging. */
    private static final Map<UUID, long[]> HANGING = new HashMap<>();
    private static Consumer<Fired> listener = e -> {
    };

    /** One fired event (dev trace). */
    public record Fired(ServerPlayer player, String event, double number) {
    }

    private RigEvents() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (e.getEntity() instanceof Spider && e.getSource().getEntity() instanceof ServerPlayer p && RigServer.state(p) != RigState.STOWED)
                fire(p, SPIDER_KILL, 1);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> HANGING.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> HANGING.clear());
    }

    public static void listen(Consumer<Fired> l) {
        listener = l;
    }

    public static void fire(ServerPlayer player, String event, double number) {
        TentaworksTriggers.RIG_EVENT.get().trigger(player, event, number);
        listener.accept(new Fired(player, event, number));
    }

    /**
     * Hanging under a ceiling: at least {@link Grips#HELD} Grips, every one at or above the body centre and at least one on the
     * underside of a block. Grips beside the wearer (a wall next to the ceiling) still count; one on the floor does not.
     */
    public static boolean hanging(double bodyY, List<Grip> grips) {
        if (grips.size() < Grips.HELD) return false;
        boolean ceiling = false;
        for (Grip g : grips) {
            if (g.point().y < bodyY) return false;
            if (g.face() == Direction.DOWN) ceiling = true;
        }
        return ceiling;
    }

    /** Each tick of a carrying Rig: fires {@value #HANG} on every whole second of one unbroken Hang (a missed tick starts it over). */
    static void hangTick(ServerPlayer player, boolean hanging) {
        if (!hanging) {
            HANGING.remove(player.getUUID());
            return;
        }
        long now = player.level().getGameTime();
        long[] h = HANGING.get(player.getUUID());
        if (h == null || h[1] != now - 1) HANGING.put(player.getUUID(), h = new long[]{now, now});
        h[1] = now;
        long ticks = now - h[0];
        if (ticks > 0 && ticks % 20 == 0) fire(player, HANG, ticks / 20.0);
    }
}
