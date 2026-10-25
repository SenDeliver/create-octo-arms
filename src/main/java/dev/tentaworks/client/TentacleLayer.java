package dev.tentaworks.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
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
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The Tentacles on every player wearing a Rig (third person, other players, inventory preview = Stowed pose), drawn in
 * the body's space on top of Create's Backtank layer. First person has no player model: the local wearer's Tentacles are
 * drawn from the level stage instead, in the body's pose (upright, or flat for a swimming or crawling wearer), without the
 * parts within reach of the eye (shoulders and upper segments behind the head).
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public class TentacleLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** The first-person Rig's own buffer: ending a batch on the shared one mid-frame frees builders other layers still hold under batching mods. */
    private static final ByteBufferBuilder FIRST_PERSON_BUFFER = new ByteBufferBuilder(256 * 1024);
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
            Matrix4f m = new Matrix4f(ps.last().pose());
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 body = player.getPosition(pt).add(0, 1.1, 0).subtract(cam);
            Vector3f c = m.transformPosition(0.5F, 0.5F, 0.5F, new Vector3f());
            boolean world = c.distance((float) body.x, (float) body.y, (float) body.z) < 1.5F;
            RigVisuals.Visual visual = RigVisuals.get(player);
            TentacleRenderer.drawRig(ps, buffers, player, rig.tier(), visual, pt,
                    new TentacleRenderer.Space(m, cam, world, false, light, lod(visual, body.length())));
            ps.popPose();
        });
    }

    /** Elbows reach this far past the body and the Grips (blocks). */
    private static final double ARM_SLACK = 2.0;

    /**
     * Culling box of a Rig's Tentacles: the body plus every Grip it holds and the pad it last drew (world coords, over this
     * frame's Ship motion), grown by the elbows' reach.
     */
    public static boolean inView(Player player, Frustum frustum) {
        RigVisuals.Visual v = RigVisuals.get(player);
        if (v == null || !v.visible()) return false;
        AABB box = player.getBoundingBox();
        for (int i = 0; i < Tentacles.SLOTS; i++) {
            RigVisuals.Slot s = v.slot(i);
            if (s.target() != null) box = box.minmax(new AABB(s.point(0), s.point(1)));
            if (s.fresh(player.tickCount)) box = box.minmax(new AABB(s.drawnTip, s.drawnTip));
        }
        return frustum.isVisible(box.inflate(ARM_SLACK));
    }

    /** Level of detail by distance, with a two-block hysteresis per Rig so parts do not pop at the boundary. */
    static int lod(RigVisuals.Visual visual, double distance) {
        int prev = visual == null ? 0 : visual.lod;
        int lod = lod(distance, prev);
        if (visual != null) visual.lod = lod;
        return lod;
    }

    static int lod(double distance, int prev) {
        double near = prev >= 1 ? 22 : 24, far = prev >= 2 ? 46 : 48;
        return distance < near ? 0 : distance < far ? 1 : 2;
    }

    @SubscribeEvent
    static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model model : event.getSkins()) {
            EntityRenderer<? extends Player> r = event.getSkin(model);
            if (r instanceof LivingEntityRenderer<?, ?> lr && lr.getModel() instanceof PlayerModel<?>) {
                @SuppressWarnings("unchecked")
                LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> pr =
                        (LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) lr;
                pr.addLayer(new TentacleLayer(pr));
            }
        }
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
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        TentacleRenderer.timed(() -> {
            Vec3 cam = event.getCamera().getPosition();
            PoseStack ps = event.getPoseStack();
            ps.pushPose();
            Vec3 at = player.getPosition(pt).subtract(cam);
            ps.translate(at.x, at.y, at.z);
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot)));
            float swim = player.getSwimAmount(pt);
            if (swim > 0) {
                float flat = player.isInWater() || player.isInFluidType((fluidType, height) -> player.canSwimInFluidType(fluidType)) ? -90.0F - player.getXRot() : -90.0F;
                ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(swim, 0.0F, flat)));
                if (player.isVisuallySwimming()) ps.translate(0.0F, -1.0F, 0.3F);
            }
            ps.scale(-1.0F, -1.0F, 1.0F);
            ps.scale(0.9375F, 0.9375F, 0.9375F);
            ps.translate(0.0F, -1.501F, 0.0F);
            TentacleRenderer.toTankSpace(ps);
            MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(FIRST_PERSON_BUFFER);
            TentacleRenderer.drawRig(ps, buffers, player, rig.tier(), visual, pt,
                    new TentacleRenderer.Space(new Matrix4f(ps.last().pose()), cam, true, true, 0xF000F0, 0));
            ps.popPose();
            buffers.endBatch();
        });
    }
}
