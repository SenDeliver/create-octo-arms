package dev.tentaworks.dev.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.client.RigVisuals;
import dev.tentaworks.rig.RigState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev overlay (DevScript {@code griplines on}): a line from the body to every Grip of every Rig the client draws (world points).
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class GripLineRenderer {
    static boolean on;

    private GripLineRenderer() {
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (!on || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (Player player : mc.level.players()) {
            RigVisuals.Visual v = RigVisuals.get(player);
            if (v == null || v.state() == RigState.STOWED) continue;
            List<Vec3> grips = new ArrayList<>();
            for (int i = 0; i < 4; i++) if (v.slot(i).target() != null) grips.add(v.slot(i).point(partial));
            Vec3 body = player.getPosition(partial).add(0, player.getBbHeight() * 0.5, 0);
            PoseStack.Pose last = pose.last();
            for (Vec3 g : grips) {
                Vec3 d = g.subtract(body).normalize();
                for (int i = 0; i < 3; i++) {
                    double o = (i - 1) * 0.03;
                    lines.addVertex(last, (float) (body.x + o), (float) body.y, (float) (body.z + o)).setColor(1.0F, 0.35F, 0.1F, 1.0F).setNormal(last, (float) d.x, (float) d.y, (float) d.z);
                    lines.addVertex(last, (float) (g.x + o), (float) g.y, (float) (g.z + o)).setColor(1.0F, 0.35F, 0.1F, 1.0F).setNormal(last, (float) d.x, (float) d.y, (float) d.z);
                }
                LevelRenderer.renderLineBox(pose, lines, new AABB(g, g).inflate(0.12), 0.1F, 1.0F, 0.3F, 1.0F);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
