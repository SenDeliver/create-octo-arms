package dev.tentaworks.client;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRidingHandler;
import net.minecraft.client.player.LocalPlayer;

/**
 * What else carries or holds the wearer on this client (adr/0003). A Ride: a vehicle or Create's chain conveyor.
 * No Control Hold exists on this branch (Simulated is 1.21-only).
 */
final class Rides {
    private Rides() {
    }

    static boolean riding(LocalPlayer player) {
        return player.isPassenger() || ChainConveyorRidingHandler.ridingChainConveyor != null;
    }

    static boolean controlHold() {
        return false;
    }
}
