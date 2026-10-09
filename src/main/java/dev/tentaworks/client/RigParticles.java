package dev.tentaworks.client;

import dev.tentaworks.rig.Grip;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** A tiny slime puff where a Tentacle plants. */
public final class RigParticles {
    private RigParticles() {
    }

    static void puff(Player player, Grip grip) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(grip.point()) > 24 * 24) return;
        Vec3 n = RigVisuals.faceNormal(grip.face());
        Vec3 p = grip.point().add(n.scale(0.08));
        RandomSource r = player.getRandom();
        for (int i = 0; i < 3; i++)
            mc.level.addParticle(ParticleTypes.ITEM_SLIME, p.x + (r.nextDouble() - 0.5) * 0.3, p.y + (r.nextDouble() - 0.5) * 0.3,
                    p.z + (r.nextDouble() - 0.5) * 0.3, n.x * 0.05, n.y * 0.05, n.z * 0.05);
    }
}
