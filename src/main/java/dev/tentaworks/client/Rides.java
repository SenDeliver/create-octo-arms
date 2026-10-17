package dev.tentaworks.client;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRidingHandler;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * What else carries or holds the wearer on this client (adr/0003). A Ride: a vehicle, Create's chain conveyor, and with
 * Simulated a zipline or a handle pulling the wearer. A Control Hold: any other Simulated hold (steering wheel, throttle
 * lever) except a handle dragging its Ship, which reads no keys and moves no one: the Rig carries on. Simulated is read by
 * reflection and only when it is loaded.
 */
final class Rides {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Simulated SIMULATED = ModList.get() != null && ModList.get().isLoaded("simulated") ? Simulated.load() : null;

    private Rides() {
    }

    static boolean riding(LocalPlayer player) {
        return player.isPassenger() || ChainConveyorRidingHandler.ridingChainConveyor != null || SIMULATED != null && SIMULATED.riding();
    }

    static boolean controlHold() {
        return SIMULATED != null && SIMULATED.controlHold();
    }

    private record Simulated(Method holding, Field zipline, Object handle, Method handleActive, Field draggingShip) {
        static Simulated load() {
            try {
                Class<?> clicks = Class.forName("dev.simulated_team.simulated.index.SimClickInteractions");
                Object handle = clicks.getField("HANDLE_HANDLER").get(null);
                return new Simulated(
                        Class.forName("dev.simulated_team.simulated.util.hold_interaction.HoldInteractionManager").getMethod("isActive"),
                        Class.forName("dev.simulated_team.simulated.content.blocks.rope.strand.client.ZiplineClientManager").getField("ridingRope"),
                        handle, handle.getClass().getMethod("isActive"), handle.getClass().getField("movingSubLevel"));
            } catch (ReflectiveOperationException | LinkageError e) {
                LOG.warn("Simulated found but its holds and ziplines are not readable: the Rig will not yield to them", e);
                return null;
            }
        }

        boolean riding() {
            try {
                return zipline.get(null) != null || pulled();
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }

        boolean controlHold() {
            try {
                return (boolean) holding.invoke(null) && !(boolean) handleActive.invoke(handle);
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }

        /** The handle moves the wearer to it, unless the hold started sneaking: then it drags its Ship instead. */
        private boolean pulled() throws ReflectiveOperationException {
            return (boolean) handleActive.invoke(handle) && !draggingShip.getBoolean(handle);
        }
    }
}
