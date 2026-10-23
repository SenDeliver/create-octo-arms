package dev.tentaworks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.TentacleRigItem;
import dev.tentaworks.rig.Tentacles;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Tentacles on every player wearing a Rig (third person, other players, inventory preview = Stowed pose), drawn in
 * the body's space on top of Create's Backtank layer. First person has no player model: the local wearer's Tentacles are
 * drawn from the level stage instead, without the parts within reach of the eye (shoulders and upper segments behind the head).
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public class TentacleLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** The first-person Rig's own buffer: ending a batch on the shared one mid-frame frees builders other layers still hold under batching mods. */
    private static final BufferBuilder FIRST_PERSON_BUFFER = new BufferBuilder(256 * 1024);
    public TentacleLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float pt, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.getPose() == Pose.SLEEPING || player.isSpectator()) return;
        if (!(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof TentacleRigItem rig)) return;
        TentacleRenderer.timed(() -> {
            ps.pushPose();
            getParentModel().body.translateAndRotate(ps);
            TentacleRenderer.toTankSpace(ps);
            Matrix4f m = toCamera(ps);
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 body = player.getPosition(pt).add(0, 1.1, 0).subtract(cam);
            Vector3f c = m.transformPosition(0.5F, 0.5F, 0.5F, new Vector3f());
            boolean world = c.distance((float) body.x, (float) body.y, (float) body.z) < 1.5F;
            TentacleRenderer.drawRig(ps, buffers, player, rig.tier(), RigVisuals.get(player), pt,
                    new TentacleRenderer.Space(m, cam, world, false, light, lod(body.length())));
            ps.popPose();
        });
    }

    /** Culling box of a Rig's Tentacles: the body plus every Grip it holds (world coords, over this frame's Ship motion). */
    public static boolean inView(Player player, Frustum frustum) {
        RigVisuals.Visual v = RigVisuals.get(player);
        if (v == null || !v.visible()) return false;
        AABB box = player.getBoundingBox();
        for (int i = 0; i < Tentacles.SLOTS; i++) {
            RigVisuals.Slot s = v.slot(i);
            if (s.target() != null) box = box.minmax(new AABB(s.point(0), s.point(1)));
        }
        return frustum.isVisible(box.inflate(0.6));
    }

    static int lod(double distance) {
        return distance < 24 ? 0 : distance < 48 ? 1 : 2;
    }

    /** The level's PoseStack carries the camera rotation on 1.20.1: undo it so {@code toCamera} maps to camera-relative world. */
    private static Matrix4f toCamera(PoseStack ps) {
        return new Matrix4f(RenderSystem.getInverseViewRotationMatrix()).mul(ps.last().pose());
    }

    @SubscribeEvent
    static void onFirstPerson(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !mc.options.getCameraType().isFirstPerson() || mc.getCameraEntity() != player || player.isSpectator()) return;
        if (!(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof TentacleRigItem rig)) return;
        RigVisuals.Visual visual = RigVisuals.get(player);
        if (visual == null || !visual.visible() || player.getPose() == Pose.SLEEPING) return;
        float pt = event.getPartialTick();
        TentacleRenderer.timed(() -> {
            Vec3 cam = event.getCamera().getPosition();
            PoseStack ps = event.getPoseStack();
            ps.pushPose();
            Vec3 at = player.getPosition(pt).subtract(cam);
            ps.translate(at.x, at.y, at.z);
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot)));
            ps.scale(-1.0F, -1.0F, 1.0F);
            ps.scale(0.9375F, 0.9375F, 0.9375F);
            ps.translate(0.0F, -1.501F, 0.0F);
            TentacleRenderer.toTankSpace(ps);
            MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(FIRST_PERSON_BUFFER);
            TentacleRenderer.drawRig(ps, buffers, player, rig.tier(), visual, pt,
                    new TentacleRenderer.Space(toCamera(ps), cam, true, true, 0xF000F0, 0));
            ps.popPose();
            buffers.endBatch();
        });
    }

    @EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Layers {
        private Layers() {
        }

        @SubscribeEvent
        static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                EntityRenderer<? extends Player> r = event.getSkin(skin);
                if (r instanceof LivingEntityRenderer<?, ?> lr && lr.getModel() instanceof PlayerModel<?>) {
                    @SuppressWarnings("unchecked")
                    LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> pr =
                            (LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) lr;
                    pr.addLayer(new TentacleLayer(pr));
                }
            }
        }
    }
}
