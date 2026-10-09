package dev.tentaworks.client.ponder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.armor.BacktankBlock;
import com.simibubi.create.content.equipment.armor.BacktankRenderer;
import dev.tentaworks.client.TentacleRenderer;
import dev.tentaworks.rig.RigBlock;
import dev.tentaworks.rig.RigTier;
import dev.tentaworks.rig.Tentacles;
import dev.tentaworks.registry.TentaworksBlocks;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A Rig wearer for Ponder (Ponder cannot run a player): a default-skin player model with the Backtank drawn like
 * Create's worn layer and the four Tentacles drawn by {@link TentacleRenderer#drawPosed}. Scene instructions move the
 * body and set Grips; every value keeps its previous tick for smooth partial ticks.
 */
public class RigPuppetElement extends AnimatedSceneElementBase {
    static final int DEPLOY_TICKS = 8;

    private final RigTier tier;
    private final Vec3 startPos;
    private final float startYaw;
    Vec3 pos, prevPos;
    float yaw, prevYaw;
    boolean active;
    float deploy, prevDeploy;
    int age;
    final Slot[] slots = new Slot[Tentacles.SLOTS];
    private PlayerModel<?> model;

    /** One Tentacle: Grip a (previous, may be null) to Grip b (current, null = free) over {@code dur} ticks. */
    static final class Slot {
        Vec3 a, an, b, bn;
        int t, dur = 1;

        void set(Vec3 point, Vec3 normal, int ticks) {
            a = b == null ? null : tipAt(progress(0));
            an = bn;
            b = point;
            bn = normal;
            t = 0;
            dur = Math.max(1, ticks);
        }

        float progress(float pt) {
            return Mth.clamp((t + pt) / dur, 0, 1);
        }

        /** Tip in scene space while both ends are Grips (arc between them), else the current Grip; null when free. */
        Vec3 tipAt(float p) {
            if (b == null) return null;
            if (a == null || p >= 1) return b;
            double lift = Math.min(1.0, 0.3 + 0.3 * a.distanceTo(b)) * Math.sin(Math.PI * p);
            Vec3 n = an.add(bn).normalize();
            return a.lerp(b, p).add(n.scale(lift));
        }
    }

    public RigPuppetElement(RigTier tier, Vec3 pos, float yaw) {
        this.tier = tier;
        this.startPos = pos;
        this.startYaw = yaw;
        restart();
    }

    void restart() {
        pos = prevPos = startPos;
        yaw = prevYaw = startYaw;
        active = false;
        deploy = prevDeploy = 0;
        age = 0;
        for (int i = 0; i < slots.length; i++) slots[i] = new Slot();
    }

    public Vec3 pos() {
        return pos;
    }

    @Override
    public void reset(PonderScene scene) {
        super.reset(scene);
        restart();
    }

    @Override
    public void tick(PonderScene scene) {
        super.tick(scene);
        age++;
        prevPos = pos;
        prevYaw = yaw;
        prevDeploy = deploy;
        deploy = Mth.clamp(deploy + (active ? 1f : -1f) / DEPLOY_TICKS, 0, 1);
        for (Slot s : slots) if (s.t < s.dur) s.t++;
    }

    @Override
    protected void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float fade, float pt) {
        if (model == null) model = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        int light = lightCoordsFromFade(fade);
        Vec3 at = prevPos.lerp(pos, pt);
        float bodyYaw = Mth.rotLerp(pt, prevYaw, yaw);
        float time = age + pt;
        float dep = Mth.lerp(pt, prevDeploy, deploy);

        PoseStack ps = graphics.pose();
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        PoseStack chain = new PoseStack();
        bodySpace(ps, bodyYaw);
        bodySpace(chain, bodyYaw);

        poseModel(time, dep);
        VertexConsumer skin = buffer.getBuffer(RenderType.entityCutoutNoCull(DefaultPlayerSkin.getDefaultTexture()));
        model.renderToBuffer(ps, skin, light, OverlayTexture.NO_OVERLAY);

        ps.pushPose();
        model.body.translateAndRotate(ps);
        drawTank(ps, buffer, world, light);
        ps.popPose();

        ps.pushPose();
        model.body.translateAndRotate(ps);
        TentacleRenderer.toTankSpace(ps);
        model.body.translateAndRotate(chain);
        TentacleRenderer.toTankSpace(chain);
        Matrix4f toTank = new Matrix4f(chain.last().pose()).invert();
        Vector3f[] tips = new Vector3f[Tentacles.SLOTS], normals = new Vector3f[Tentacles.SLOTS];
        float[] attach = new float[Tentacles.SLOTS];
        for (int i = 0; i < Tentacles.SLOTS; i++) {
            Slot s = slots[i];
            float p = s.progress(pt);
            Vec3 tip = s.tipAt(p), n = s.bn;
            attach[i] = 1;
            if (s.b != null && s.a == null) attach[i] = p;
            if (s.b == null && s.a != null) {
                tip = s.a;
                n = s.an;
                attach[i] = 1 - p;
            }
            if (tip == null) continue;
            Vec3 local = tip.subtract(at);
            tips[i] = toTank.transformPosition((float) local.x, (float) local.y, (float) local.z, new Vector3f());
            normals[i] = toTank.transformDirection((float) n.x, (float) n.y, (float) n.z, new Vector3f()).normalize();
        }
        TentacleRenderer.drawPosed(ps, buffer, tier, tips, normals, attach, dep, time, light);
        ps.popPose();
        ps.popPose();
    }

    /** Living-entity render space at the feet (as LivingEntityRenderer: body yaw, flipped, 1.501 down). */
    private static void bodySpace(PoseStack ps, float yaw) {
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.translate(0.0F, -1.501F, 0.0F);
    }

    private void poseModel(float time, float dep) {
        model.setAllVisible(true);
        model.young = false;
        model.crouching = false;
        model.riding = false;
        model.head.xRot = 0.15F;
        model.head.yRot = 0;
        float swing = Mth.sin(time * 0.12F) * 0.12F * dep;
        model.rightArm.xRot = -0.25F * dep + swing;
        model.leftArm.xRot = -0.25F * dep - swing;
        model.rightArm.zRot = 0.08F;
        model.leftArm.zRot = -0.08F;
        model.rightLeg.xRot = 0.1F * dep - swing * 0.6F;
        model.leftLeg.xRot = -0.1F * dep + swing * 0.6F;
        model.rightArm.yRot = model.leftArm.yRot = model.rightLeg.yRot = model.leftLeg.yRot = 0;
        model.body.xRot = model.body.yRot = model.body.zRot = 0;
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }

    /** Create's BacktankArmorLayer, on the plain (no folded arms) Rig Block state. */
    private void drawTank(PoseStack ps, MultiBufferSource buffer, PonderLevel world, int light) {
        RigBlock block = tier == RigTier.NETHERITE ? TentaworksBlocks.NETHERITE_TENTACLE_RIG.get() : TentaworksBlocks.COPPER_TENTACLE_RIG.get();
        BlockState state = block.defaultBlockState().setValue(BacktankBlock.HORIZONTAL_FACING, Direction.SOUTH);
        VertexConsumer vc = buffer.getBuffer(Sheets.cutoutBlockSheet());
        SuperByteBuffer tank = CachedBuffers.block(state);
        SuperByteBuffer cogs = CachedBuffers.partial(BacktankRenderer.getCogsModel(state), state);
        SuperByteBuffer nob = CachedBuffers.partial(BacktankRenderer.getShaftModel(state), state);
        ps.translate(-0.5F, 0.625F, 1.0F);
        ps.scale(1.0F, -1.0F, -1.0F);
        tank.disableDiffuse().light(light).renderInto(ps, vc);
        nob.disableDiffuse().translate(0.0F, -0.1875F, 0.0F).light(light).renderInto(ps, vc);
        cogs.center().rotateYDegrees(180.0F).uncenter().translate(0.0F, 0.40625F, 0.6875F)
                .rotate(AngleHelper.rad(2.0F * AnimationTickHolder.getRenderTime(world) % 360.0F), Direction.EAST)
                .translate(0.0F, -0.40625F, -0.6875F);
        cogs.disableDiffuse().light(light).renderInto(ps, vc);
    }
}
