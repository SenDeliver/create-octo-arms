package dev.tentaworks.registry;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.tterrag.registrate.util.entry.RegistryEntry;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.display.RigAirDisplaySource;

import static dev.tentaworks.Tentaworks.REGISTRATE;

public final class TentaworksDisplaySources {
    public static final RegistryEntry<DisplaySource, RigAirDisplaySource> RIG_AIR =
            REGISTRATE.displaySource(TentaworksIds.RIG_AIR_SOURCE, RigAirDisplaySource::new).register();

    private TentaworksDisplaySources() {
    }

    public static void init() {
    }
}
