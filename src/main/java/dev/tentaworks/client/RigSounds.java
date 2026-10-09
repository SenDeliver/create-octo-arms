package dev.tentaworks.client;

import com.simibubi.create.AllSoundEvents;
import dev.tentaworks.rig.Grip;
import dev.tentaworks.rig.RigState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Rig sounds, played on every client that sees the Rig (positional, players category): the Sticker's slime squish on a
 * Step plant (and a soft one on lift), Create's steam hiss when the height starts changing, piston extend / retract on
 * activate / stow, and a quiet cog rumble loop while moving.
 */
public final class RigSounds {
    private static final double HEAR = 32;

    private RigSounds() {
    }

    static boolean plant(Player player, Grip grip) {
        return at(player, grip.point(), AllSoundEvents.SLIME_ADDED.getMainEvent(), 0.3F, 0.75F + 0.1F * player.getRandom().nextFloat());
    }

    static void lift(Player player, Grip grip) {
        at(player, grip.point(), AllSoundEvents.SLIME_ADDED.getMainEvent(), 0.12F, 0.5F);
    }

    static void hiss(Player player, float volume, float pitch) {
        at(player, player.position().add(0, 1.2, 0), AllSoundEvents.STEAM.getMainEvent(), volume, pitch);
    }

    static void activate(Player player, boolean on) {
        Vec3 p = player.position().add(0, 1.2, 0);
        at(player, p, on ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT, 0.3F, on ? 1.3F : 1.1F);
        hiss(player, 0.15F, on ? 1.1F : 0.9F);
    }

    static void letGo(Player player) {
        at(player, player.position().add(0, 1.2, 0), AllSoundEvents.SLIME_ADDED.getMainEvent(), 0.25F, 0.5F);
    }

    static void whir(Player player, RigVisuals.Visual v) {
        if (v.speed < 0.05 || v.state == RigState.STOWED) return;
        if (v.whir != null && !v.whir.isStopped()) return;
        if (player.tickCount - v.whirStarted < 40) return;
        v.whirStarted = player.tickCount;
        if (Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(player.position()) > HEAR * HEAR) return;
        v.whir = new Whir(player, v);
        Minecraft.getInstance().getSoundManager().play(v.whir);
    }

    static boolean inHearing(Vec3 p) {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(p) <= HEAR * HEAR;
    }

    private static boolean at(Player player, Vec3 p, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !inHearing(p)) return false;
        mc.level.playLocalSound(p.x, p.y, p.z, sound, SoundSource.PLAYERS, volume, pitch, false);
        return true;
    }

    static final class Whir extends AbstractTickableSoundInstance {
        private final Player player;
        private final RigVisuals.Visual visual;
        private int quiet;

        Whir(Player player, RigVisuals.Visual visual) {
            super(AllSoundEvents.COGS.getMainEvent(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.visual = visual;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = 1.4F;
            follow();
        }

        private void follow() {
            x = player.getX();
            y = player.getY() + 1.2;
            z = player.getZ();
        }

        @Override
        public void tick() {
            if (player.isRemoved() || visual.state == RigState.STOWED) {
                stop();
                return;
            }
            quiet = visual.speed < 0.03 ? quiet + 1 : 0;
            if (quiet > 10) {
                stop();
                return;
            }
            volume = (float) Mth.clamp(visual.speed * 0.5, 0.01, 0.15);
            pitch = (float) Mth.clamp(1.1 + visual.speed, 1.1, 1.6);
            follow();
        }

        void stopNow() {
            stop();
        }
    }
}
