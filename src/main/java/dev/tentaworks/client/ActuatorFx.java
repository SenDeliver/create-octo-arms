package dev.tentaworks.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.SetDown;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Throw and Impact feedback: the thrower's camera kicks a little and the arms follow through; an Impact (told by the server)
 * sparks and thuds like a body landing (a mob's hurt sound comes with its damage); only an Iron Golem clanks. While holding, the
 * spot a Set Down would put the mob on ({@link ActuatorClient#setDownSpot}) is outlined in the world: its footprint, and its box faintly,
 * red where it may not go.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class ActuatorFx {
    private static final float KICK_DEGREES = 2.5F, KICK_TICKS = 5;
    /** Impact speed (blocks per tick) from which a body lands with the big fall thud. */
    private static final double BIG_THUD_SPEED = 1.0;
    private static int kickTick = Integer.MIN_VALUE / 2;

    private ActuatorFx() {
    }

    /** {@code wearer} threw what it held along {@code dir}. */
    static void thrown(Player wearer, Vec3 dir) {
        ActuatorRenderer.thrown(wearer, dir);
        if (wearer == Minecraft.getInstance().player) kickTick = wearer.tickCount;
    }

    /** An Impact of {@code thrown} at {@code at}, flying at {@code speed} blocks per tick: sparks, dust and a sound that fits it. */
    static void impact(int thrown, Vec3 at, double speed) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource r = level.random;
        Entity e = level.getEntity(thrown);
        boolean metal = e instanceof IronGolem;
        int sparks = Mth.clamp((int) (speed * 12), 4, 16);
        for (int i = 0; i < (metal ? sparks : sparks / 3); i++) {
            Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian() + 0.4, r.nextGaussian()).scale(0.25);
            level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 4; i++)
            level.addParticle(ParticleTypes.POOF, at.x + r.nextGaussian() * 0.2, at.y + r.nextGaussian() * 0.2, at.z + r.nextGaussian() * 0.2, 0, 0.02, 0);
        float volume = (float) Mth.clamp(0.25 + speed * 0.3, 0.25, 0.7);
        if (metal) {
            level.playLocalSound(at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, volume, 1.5F + r.nextFloat() * 0.2F, false);
            return;
        }
        boolean big = speed > BIG_THUD_SPEED;
        SoundEvent thud = e instanceof LivingEntity body ? big ? body.getFallSounds().big() : body.getFallSounds().small()
                : big ? SoundEvents.GENERIC_BIG_FALL : SoundEvents.GENERIC_SMALL_FALL;
        level.playLocalSound(at.x, at.y, at.z, thud, e == null ? SoundSource.NEUTRAL : e.getSoundSource(), volume, 0.85F + r.nextFloat() * 0.15F, false);
    }

    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float age = mc.player.tickCount - kickTick + (float) event.getPartialTick();
        if (age < 0 || age >= KICK_TICKS) return;
        float k = 1 - age / KICK_TICKS;
        event.setPitch(event.getPitch() - KICK_DEGREES * k * k);
    }

    @SubscribeEvent
    static void onLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator()) return;
        SetDown.Spot spot = ActuatorClient.setDownSpot();
        if (spot == null) return;
        Vec3 cam = event.getCamera().getPosition();
        AABB box = spot.box().move(-cam.x, -cam.y, -cam.z).inflate(0.01, 0, 0.01);
        float r = 1, g = spot.valid() ? 1 : 0.25F, b = spot.valid() ? 1 : 0.25F;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(event.getPoseStack(), lines, new AABB(box.minX, box.minY + 0.005, box.minZ, box.maxX, box.minY + 0.07, box.maxZ), r, g, b, 0.95F);
        LevelRenderer.renderLineBox(event.getPoseStack(), lines, new AABB(box.minX, box.minY + 0.07, box.minZ, box.maxX, box.maxY, box.maxZ), r, g, b, 0.3F);
        buffers.endBatch(RenderType.lines());
    }
}
