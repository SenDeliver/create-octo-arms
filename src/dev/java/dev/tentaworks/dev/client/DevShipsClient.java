package dev.tentaworks.dev.client;

import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.ArrayList;
import java.util.List;

/** Dev source set, Sable only (loaded only when Sable is): the client's logical pose of every sub-level for CSHIP lines. */
final class DevShipsClient {
    private DevShipsClient() {
    }

    /** {@code id=<uuid> pos=x,y,z q=x,y,z,w} per sub-level. */
    static List<String> poses(ClientLevel level) {
        List<String> out = new ArrayList<>();
        ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return out;
        for (ClientSubLevel s : container.getAllSubLevels()) {
            if (s.isRemoved()) continue;
            Pose3dc p = s.logicalPose();
            out.add(String.format("id=%s pos=%.3f,%.3f,%.3f q=%.5f,%.5f,%.5f,%.5f", s.getUniqueId(), p.position().x(), p.position().y(), p.position().z(),
                    p.orientation().x(), p.orientation().y(), p.orientation().z(), p.orientation().w()));
        }
        return out;
    }
}
