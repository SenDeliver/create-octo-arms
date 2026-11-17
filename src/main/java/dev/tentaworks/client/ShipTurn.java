package dev.tentaworks.client;

import dev.tentaworks.network.RigGrabPacket;
import dev.tentaworks.rig.Hold;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turn, the wearer's side (CONTEXT: Ship Grab): while a Ship is held with the Grab key and use both held, the camera stays still
 * and the mouse turns the Ship instead ({@code MouseHandlerTurnMixin} hands over the look delta, vanilla sensitivity and invert
 * already in it): left and right its yaw about world up, up and down its pitch about the wearer's horizontal right, as the
 * Physics Staff of Simulated rotates. The degrees are summed and sent once a tick, at most {@value #PACKETS} packets of
 * {@link Hold#TURN_MAX} each. Whether it turns is asked anew on every mouse move: the key or use up, a screen, the hold gone,
 * death or a new world give the look back at once.
 */
public final class ShipTurn {
    /** Degrees per unit of {@code Entity.turn}. */
    static final double DEGREES = 0.15;
    static final int PACKETS = 2;
    private static double yaw, pitch;

    private ShipTurn() {
    }

    /** The wearer is Turning a held Ship now. */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return player != null && mc.level != null && mc.screen == null && player.isAlive() && !player.isSpectator() && TentaworksKeys.GRAB.isDown()
                && mc.options.keyUse.isDown() && ActuatorClient.local().ship() != null;
    }

    /** A look delta in {@code Entity.turn} units: true when it turns the held Ship instead of the camera. */
    public static boolean swallow(double dx, double dy) {
        if (!active()) return false;
        if (Double.isFinite(dx) && Double.isFinite(dy)) {
            yaw += dx * DEGREES;
            pitch += dy * DEGREES;
        }
        return true;
    }

    /** One client tick: this tick's degrees to the server; nothing kept while not Turning. */
    static void tick() {
        if (!active()) {
            yaw = pitch = 0;
            return;
        }
        for (int i = 0; i < PACKETS && (yaw != 0 || pitch != 0); i++) {
            float y = (float) Mth.clamp(yaw, -Hold.TURN_MAX, Hold.TURN_MAX), p = (float) Mth.clamp(pitch, -Hold.TURN_MAX, Hold.TURN_MAX);
            PacketDistributor.sendToServer(RigGrabPacket.turn(y, p));
            yaw -= y;
            pitch -= p;
        }
        yaw = pitch = 0;
    }
}
