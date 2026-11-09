package dev.tentaworks.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tentaworks.config.TentaworksClientConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * The one vertex consumer all Limbs of a Rig are drawn with in a pass: solid cutout, or in first person at the client config's
 * opacity (translucent below 1). Take it once per pass: asking a buffer source for another type ends the batch it came from.
 */
final class LimbBuffer {
    private LimbBuffer() {
    }

    static VertexConsumer get(MultiBufferSource buffers, boolean firstPerson) {
        int alpha = firstPerson ? ActuatorPose.alpha(TentaworksClientConfig.limbOpacity()) : 255;
        if (alpha >= 255) return buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        return new Faded(buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS)), alpha);
    }

    private record Faded(VertexConsumer inner, int alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int a) {
            inner.setColor(red, green, blue, a * alpha / 255);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            inner.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }
    }
}
