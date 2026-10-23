package dev.tentaworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.tentaworks.rig.RigTier;
import dev.tentaworks.rig.Tentacles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Function;

/**
 * Draws the four Tentacles of a Rig in tank space: the Backtank block model's own coordinates (blocks, model native
 * orientation: -Z toward the wearer's back, -X the wearer's left, +Y up), so shoulders sit on the drawn tank.
 * Each Tentacle: shoulder hub on the tank side, telescoping upper segment (sleeve, collar, rod), elbow hub with cogs,
 * forearm, wrist and a Sticker pad whose slime face lies flush on the gripped face. Stowed = folded against the tank;
 * Active = two-segment bend from shoulder to Grip (elbow pushed outward and away from the face). Lighting per part from
 * its world position.
 */
public final class TentacleRenderer {
    public static final float PX = 1 / 16f;
    static final float PAD = 2f * PX;
    static final float STEM = 1.5f * PX;
    private static final ResourceLocation STICKER = new ResourceLocation("create", "block/sticker");

    /** Shoulder hub centres (px), slots 0 front-left/upper-left, 1 front-right/upper-right, 2 back-left/lower-left, 3 back-right/lower-right. */
    static final Vector3f[] SHOULDER = {v(2.75f, 10.0f, 7.5f), v(13.25f, 10.0f, 7.5f), v(2.75f, 3.0f, 7.5f), v(13.25f, 3.0f, 7.5f)};
    /** Stowed pose (px): elbow, wrist and pad normal (the slime face looks along -normal: down, out of sight from above). */
    static final Vector3f[] FOLD_ELBOW = {v(2.75f, 13.5f, 7.5f), v(13.25f, 13.5f, 7.5f), v(2.75f, 6.5f, 7.5f), v(13.25f, 6.5f, 7.5f)};
    static final Vector3f[] FOLD_WRIST = {v(2.75f, 13.5f, 11.0f), v(13.25f, 13.5f, 11.0f), v(2.75f, 6.5f, 11.0f), v(13.25f, 6.5f, 11.0f)};
    static final Vector3f[] FOLD_NORMAL = {v(0, 1, 0), v(0, 1, 0), v(0, 1, 0), v(0, 1, 0)};

    private static long nanos;
    private static int calls;

    public record Style(ResourceLocation sleeve, ResourceLocation rod, ResourceLocation collar, ResourceLocation hub, ResourceLocation cog) {
        static Style of(RigTier tier) {
            String s = tier == RigTier.NETHERITE ? "_netherite" : "";
            return new Style(tex("tentacle_sleeve" + s), tex("tentacle_rod" + s), tex("tentacle_collar" + s), tex("tentacle_hub" + s), tex("tentacle_cog" + s));
        }

        private static ResourceLocation tex(String name) {
            return dev.tentaworks.TentaworksIds.id("block/" + name);
        }
    }

    /** Where to light and cull: tank space → camera-relative world, the camera position, and whether this is a world render. */
    record Space(Matrix4f toCamera, Vec3 camera, boolean world, boolean firstPerson, int fallbackLight, int lod) {
        int light(Level level, Vector3f tankPoint) {
            if (!world) return fallbackLight;
            Vector3f w = toCamera.transformPosition(tankPoint, new Vector3f());
            BlockPos pos = BlockPos.containing(w.x + camera.x, w.y + camera.y, w.z + camera.z);
            return LevelRenderer.getLightColor(level, pos);
        }

        /** First person only: is the local segment a→b (blocks, along Y) closer than {@code radius} to the eye? */
        boolean nearCamera(PoseStack.Pose pose, float x, float y0, float y1, float z, float radius) {
            if (!firstPerson) return false;
            Vector3f a = toCamera.transformPosition(pose.pose().transformPosition(x, y0, z, new Vector3f()));
            Vector3f b = toCamera.transformPosition(pose.pose().transformPosition(x, y1, z, new Vector3f()));
            Vector3f ab = new Vector3f(b).sub(a);
            float t = ab.lengthSquared() < 1.0E-8F ? 0 : Mth.clamp(-a.dot(ab) / ab.lengthSquared(), 0, 1);
            return new Vector3f(a).fma(t, ab).length() < radius;
        }
    }

    /** One drawn Tentacle in tank space (blocks): shoulder, elbow, wrist, pad contact point and the gripped face's normal. */
    record Arm(Vector3f s, Vector3f e, Vector3f w, Vector3f t, Vector3f n) {
    }

    private TentacleRenderer() {
    }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    public static void stats(java.util.function.BiConsumer<Long, Integer> sink) {
        sink.accept(nanos, calls);
        nanos = 0;
        calls = 0;
    }

    /** Pushes the Backtank layer's block transform (Create's BacktankArmorLayer, south-facing model) and the 180° model turn: tank space. */
    public static void toTankSpace(PoseStack ps) {
        ps.translate(-0.5F, 0.625F, 1.0F);
        ps.scale(1.0F, -1.0F, -1.0F);
        ps.translate(0.5F, 0, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        ps.translate(-0.5F, 0, -0.5F);
    }

    static Arm folded(int slot) {
        Vector3f s = new Vector3f(SHOULDER[slot]).mul(PX);
        Vector3f e = new Vector3f(FOLD_ELBOW[slot]).mul(PX);
        Vector3f w = new Vector3f(FOLD_WRIST[slot]).mul(PX);
        Vector3f n = new Vector3f(FOLD_NORMAL[slot]);
        Vector3f t = new Vector3f(w).fma(-(PAD + STEM), n);
        return new Arm(s, e, w, t, n);
    }

    static Vector3f outward(int slot) {
        return new Vector3f((slot & 1) == 0 ? -1 : 1, 0, 0);
    }

    /** Free (spare or Falling) Tentacle: hanging out and down from the shoulder, swaying a little. */
    static Arm free(int slot, float time) {
        Vector3f s = new Vector3f(SHOULDER[slot]).mul(PX);
        float sway = Mth.sin(time * 0.08F + slot * 1.7F) * 0.08F;
        Vector3f t = new Vector3f(s).add(new Vector3f(outward(slot)).mul(0.75F + sway)).add(0, -0.85F, slot < 2 ? -0.35F + sway : 0.45F);
        Vector3f n = new Vector3f(outward(slot)).mul(0.35F).add(0, 1, 0).normalize();
        return reach(s, t, n, slot);
    }

    /** Active Tentacle to a pad contact point {@code t} on a face with normal {@code n} (tank space). */
    static Arm reach(Vector3f s, Vector3f t, Vector3f n, int slot) {
        Vector3f w = new Vector3f(t).fma(PAD + STEM, n);
        Vector3f d = new Vector3f(w).sub(s);
        float len = d.length();
        Vector3f u = len < 1.0E-4F ? new Vector3f(0, 1, 0) : new Vector3f(d).div(len);
        Vector3f p = new Vector3f(outward(slot)).mul(1.5F).add(new Vector3f(n).mul(1.0F)).add(0, 0.3F, 0);
        p.fma(-p.dot(u), u);
        if (p.lengthSquared() < 1.0E-4F) {
            p.set(outward(slot)).fma(-outward(slot).dot(u), u);
            if (p.lengthSquared() < 1.0E-4F) p.set(0, 0, 1);
        }
        p.normalize();
        float h = Mth.clamp(0.4F * len + 0.05F, 0.18F, 2.6F);
        Vector3f e = new Vector3f(s).fma(0.4F, d).fma(h, p);
        return new Arm(s, e, w, t, n);
    }

    static Arm blend(Arm a, Arm b, float k) {
        if (k <= 0) return a;
        if (k >= 1) return b;
        Vector3f n = new Vector3f(a.n).lerp(b.n, k);
        if (n.lengthSquared() < 1.0E-4F) n.set(b.n);
        n.normalize();
        Vector3f t = new Vector3f(a.t).lerp(b.t, k);
        Vector3f w = new Vector3f(t).fma(PAD + STEM, n);
        return new Arm(a.s, new Vector3f(a.e).lerp(b.e, k), w, t, n);
    }

    static void timed(Runnable r) {
        long t0 = System.nanoTime();
        r.run();
        nanos += System.nanoTime() - t0;
        calls++;
    }

    /**
     * Draws a Rig's Tentacles. {@code ps} is in tank space; {@code space} maps it to the world (for lights, Grips and the first-person cut).
     */
    static void drawRig(PoseStack ps, MultiBufferSource buffers, Player player, RigTier tier, RigVisuals.Visual visual, float pt, Space space) {
        Style style = Style.of(tier);
        Function<ResourceLocation, TextureAtlasSprite> atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        float deploy = visual == null || !space.world ? 0 : visual.deploy(pt);
        Matrix4f toTank = deploy > 0 ? new Matrix4f(space.toCamera).invert() : null;
        float time = player.tickCount + pt;
        for (int i = 0; i < Tentacles.SLOTS; i++) {
            Arm arm = folded(i);
            if (deploy > 0) {
                float k = (float) RigVisuals.smooth(deploy * 1.6F - i * 0.2F);
                if (k > 0) {
                    RigVisuals.Slot slot = visual.slot(i);
                    Arm free = free(i, time);
                    Arm out = free;
                    if (slot.target() != null) {
                        Vector3f freeTipCam = space.toCamera.transformPosition(free.t, new Vector3f());
                        Vec3 freeWorld = new Vec3(freeTipCam.x + space.camera.x, freeTipCam.y + space.camera.y, freeTipCam.z + space.camera.z);
                        Vec3 tip = slot.tip(pt, freeWorld);
                        Vec3 n = slot.normal(pt);
                        Vector3f t = toTank.transformPosition((float) (tip.x - space.camera.x), (float) (tip.y - space.camera.y), (float) (tip.z - space.camera.z), new Vector3f());
                        Vector3f nt = toTank.transformDirection((float) n.x, (float) n.y, (float) n.z, new Vector3f()).normalize();
                        if (t.isFinite() && t.distance(free.s) <= RigVisuals.MAX_GRIP_DISTANCE) out = reach(free.s, t, nt, i);
                    }
                    arm = blend(arm, out, k);
                }
            }
            drawArm(ps, vc, atlas, style, arm, player.level(), space);
        }
    }

    /**
     * BE-free posed drawing (Ponder): {@code ps} is in tank space. Per slot: {@code attach} blends the free arm (0) into
     * the arm reaching {@code tips[i]} on a face with normal {@code normals[i]} (1, tank space; null tip = free), and
     * {@code deploy} blends the folded pose (0) into that (1), staggered per slot like a worn Rig.
     */
    public static void drawPosed(PoseStack ps, MultiBufferSource buffers, RigTier tier, Vector3f[] tips, Vector3f[] normals, float[] attach,
                                 float deploy, float time, int light) {
        Style style = Style.of(tier);
        Function<ResourceLocation, TextureAtlasSprite> atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        Space space = new Space(new Matrix4f(), Vec3.ZERO, false, false, light, 0);
        for (int i = 0; i < Tentacles.SLOTS; i++) {
            Arm arm = folded(i);
            float k = (float) RigVisuals.smooth(deploy * 1.6F - i * 0.2F);
            if (k > 0) {
                Arm out = free(i, time);
                if (tips[i] != null && attach[i] > 0) out = blend(out, reach(out.s, tips[i], normals[i], i), (float) RigVisuals.smooth(attach[i]));
                arm = blend(arm, out, k);
            }
            drawArm(ps, vc, atlas, style, arm, null, space);
        }
    }

    static void drawArm(PoseStack ps, VertexConsumer vc, Function<ResourceLocation, TextureAtlasSprite> atlas, Style style, Arm a, Level level, Space space) {
        TextureAtlasSprite sleeve = atlas.apply(style.sleeve), rod = atlas.apply(style.rod), collar = atlas.apply(style.collar),
                hub = atlas.apply(style.hub), cog = atlas.apply(style.cog), sticker = atlas.apply(STICKER);
        Vector3f u1 = new Vector3f(a.e).sub(a.s), u2 = new Vector3f(a.w).sub(a.e);
        Vector3f hinge = new Vector3f(u1).cross(u2);
        if (hinge.lengthSquared() < 1.0E-8F) hinge.set(1, 0, 0);
        hinge.normalize();
        boolean detail = space.lod < 1, hubs = space.lod < 2;

        int ls = space.light(level, a.s), le = space.light(level, a.e), lw = space.light(level, new Vector3f(a.w).fma(0.2F, a.n));
        Vector3f midUpper = new Vector3f(a.s).add(a.e).mul(0.5F), midFore = new Vector3f(a.e).add(a.w).mul(0.5F);
        int lu = space.light(level, midUpper), lf = space.light(level, midFore);

        ps.pushPose();
        ps.translate(a.s.x, a.s.y, a.s.z);
        if (hubs) {
            box(ps.last(), vc, hub, ls, space, -1.75f, 1.75f, -1.75f, 1.75f, -1.75f, 1.75f, Mode.BLOCK);
            if (detail) decalX(ps.last(), vc, cog, ls, space, outward0(a.s) * 1.8f, 4.0f);
        }
        ps.popPose();

        float l1 = segment(ps, a.s, a.e, hinge);
        float sl = Math.max(0.6f * l1, 2.5f * PX);
        box(ps.last(), vc, sleeve, lu, space, -1.5f, 1.5f, 1.0f, sl / PX - 0.15f, -1.5f, 1.5f, Mode.TUBE);
        if (detail) {
            if (sl / PX >= 4.5f) box(ps.last(), vc, collar, lu, space, -1.9f, 1.9f, 1.75f, 2.75f, -1.9f, 1.9f, Mode.BAND);
            box(ps.last(), vc, collar, lu, space, -1.9f, 1.9f, sl / PX - 1.2f, sl / PX, -1.9f, 1.9f, Mode.BAND);
        }
        box(ps.last(), vc, rod, lu, space, -1f, 1f, sl / PX - 1.0f, l1 / PX, -1f, 1f, Mode.TUBE);
        ps.popPose();

        segment(ps, a.e, a.e, hinge, u1, u2);
        if (hubs) {
            box(ps.last(), vc, hub, le, space, -1.5f, 1.5f, -1.65f, 1.65f, -1.65f, 1.65f, Mode.BLOCK);
            if (detail) {
                box(ps.last(), vc, rod, le, space, -2.5f, 2.5f, -0.6f, 0.6f, -0.6f, 0.6f, Mode.AXLE);
                decalX(ps.last(), vc, cog, le, space, 2.0f, 3.6f);
                decalX(ps.last(), vc, cog, le, space, -2.0f, 3.6f);
            }
        }
        ps.popPose();

        float l2 = segment(ps, a.e, a.w, hinge);
        float fl = Math.max(0.55f * l2, 2f * PX);
        box(ps.last(), vc, sleeve, lf, space, -1.2f, 1.2f, 1.5f, fl / PX - 0.15f, -1.2f, 1.2f, Mode.TUBE);
        if (detail) box(ps.last(), vc, collar, lf, space, -1.55f, 1.55f, fl / PX - 1.0f, fl / PX, -1.55f, 1.55f, Mode.BAND);
        box(ps.last(), vc, rod, lf, space, -0.75f, 0.75f, fl / PX - 1f, l2 / PX, -0.75f, 0.75f, Mode.TUBE);
        if (hubs) box(ps.last(), vc, hub, lw, space, -1.25f, 1.25f, l2 / PX - 1.25f, l2 / PX + 1.25f, -1.25f, 1.25f, Mode.BLOCK);
        ps.popPose();

        Vector3f padUp = new Vector3f(a.n);
        Vector3f side = new Vector3f(hinge).fma(-hinge.dot(padUp), padUp);
        if (side.lengthSquared() < 1.0E-4F) side.set(Math.abs(padUp.x) < 0.9F ? 1 : 0, Math.abs(padUp.x) < 0.9F ? 0 : 1, 0).fma(-padUp.dot(side.x, side.y, side.z), padUp);
        frame(ps, a.t, padUp, side.normalize());
        pad(ps.last(), vc, sticker, lw, space);
        box(ps.last(), vc, rod, lw, space, -0.75f, 0.75f, PAD / PX, (PAD + STEM) / PX + 0.5f, -0.75f, 0.75f, Mode.TUBE);
        ps.popPose();
    }

    private static float outward0(Vector3f s) {
        return s.x < 0.5f ? -1 : 1;
    }

    /** Pushes a frame at {@code a}: +Y toward {@code b}, +X along the hinge (projected). Returns the length; caller pops. */
    private static float segment(PoseStack ps, Vector3f a, Vector3f b, Vector3f hinge) {
        Vector3f y = new Vector3f(b).sub(a);
        float len = y.length();
        if (len < 1.0E-5F) y.set(0, 1, 0);
        else y.div(len);
        frame(ps, a, y, hinge);
        return len;
    }

    /** Elbow frame: +Y halfway between the two segment directions, +X the hinge. */
    private static void segment(PoseStack ps, Vector3f at, Vector3f unused, Vector3f hinge, Vector3f u1, Vector3f u2) {
        Vector3f y = new Vector3f(u1).normalize().add(new Vector3f(u2).normalize());
        if (y.lengthSquared() < 1.0E-6F) y.set(u1);
        frame(ps, at, y.normalize(), hinge);
    }

    private static void frame(PoseStack ps, Vector3f origin, Vector3f yAxis, Vector3f xHint) {
        Vector3f y = new Vector3f(yAxis).normalize();
        Vector3f x = new Vector3f(xHint).fma(-xHint.dot(y), y);
        if (x.lengthSquared() < 1.0E-6F) {
            x.set(Math.abs(y.x) < 0.9F ? 1 : 0, Math.abs(y.x) < 0.9F ? 0 : 1, 0);
            x.fma(-x.dot(y), y);
        }
        x.normalize();
        Vector3f z = new Vector3f(x).cross(y).normalize();
        Matrix3f m = new Matrix3f(x, y, z);
        ps.pushPose();
        ps.translate(origin.x, origin.y, origin.z);
        ps.mulPose(new Quaternionf().setFromNormalized(m));
    }

    enum Mode { TUBE, BLOCK, BAND, AXLE }

    /** Blocks of sleeve a TUBE may tile; a longer one is a bad pose (unresolved Ship), never a real arm. */
    private static final int MAX_TUBE_RINGS = 64;
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static long lastCapLog;

    /** Box in px around the current frame. TUBE: sides tile V once per block, U centred; BLOCK: whole texture per face; BAND: centre rows; AXLE: a rod along X. */
    private static void box(PoseStack.Pose p, VertexConsumer vc, TextureAtlasSprite s, int light, Space space,
                            float x0, float x1, float y0, float y1, float z0, float z1, Mode mode) {
        if (y1 - y0 < 0.01f) return;
        if (space.nearCamera(p, (x0 + x1) / 2 * PX, y0 * PX, y1 * PX, (z0 + z1) / 2 * PX, 0.6f + Math.max(x1 - x0, z1 - z0) * PX)) return;
        float X0 = x0 * PX, X1 = x1 * PX, Y0 = y0 * PX, Y1 = y1 * PX, Z0 = z0 * PX, Z1 = z1 * PX;
        float w = x1 - x0, d = z1 - z0, h = y1 - y0;
        switch (mode) {
            case BLOCK -> {
                quad(vc, p, s, light, 0, 1, 0, 0, 16, 0, 16, X0, Y1, Z0, X1, Y1, Z0, X1, Y1, Z1, X0, Y1, Z1);
                quad(vc, p, s, light, 0, -1, 0, 0, 16, 0, 16, X0, Y0, Z1, X1, Y0, Z1, X1, Y0, Z0, X0, Y0, Z0);
                sides(vc, p, s, light, X0, X1, Y0, Y1, Z0, Z1, 0, 16, 0, 16, 0, 16);
            }
            case BAND -> {
                caps(vc, p, s, light, X0, X1, Y0, Y1, Z0, Z1, 8 - w / 2, 8 + w / 2, 8 - d / 2, 8 + d / 2);
                sides(vc, p, s, light, X0, X1, Y0, Y1, Z0, Z1, 0, 16, 0, 16, 8 - h / 2, 8 + h / 2);
            }
            case AXLE -> {
                quad(vc, p, s, light, -1, 0, 0, 8 - d / 2, 8 + d / 2, 8 - h / 2, 8 + h / 2, X0, Y1, Z0, X0, Y1, Z1, X0, Y0, Z1, X0, Y0, Z0);
                quad(vc, p, s, light, 1, 0, 0, 8 - d / 2, 8 + d / 2, 8 - h / 2, 8 + h / 2, X1, Y1, Z1, X1, Y1, Z0, X1, Y0, Z0, X1, Y0, Z1);
                quad(vc, p, s, light, 0, 1, 0, 0, 16, 8 - d / 2, 8 + d / 2, X0, Y1, Z0, X1, Y1, Z0, X1, Y1, Z1, X0, Y1, Z1);
                quad(vc, p, s, light, 0, -1, 0, 0, 16, 8 - d / 2, 8 + d / 2, X0, Y0, Z1, X1, Y0, Z1, X1, Y0, Z0, X0, Y0, Z0);
                quad(vc, p, s, light, 0, 0, -1, 0, 16, 8 - h / 2, 8 + h / 2, X1, Y1, Z0, X0, Y1, Z0, X0, Y0, Z0, X1, Y0, Z0);
                quad(vc, p, s, light, 0, 0, 1, 0, 16, 8 - h / 2, 8 + h / 2, X0, Y1, Z1, X1, Y1, Z1, X1, Y0, Z1, X0, Y0, Z1);
            }
            case TUBE -> {
                caps(vc, p, s, light, X0, X1, Y0, Y1, Z0, Z1, 8 - w / 2, 8 + w / 2, 8 - d / 2, 8 + d / 2);
                float span = Y1 - Y0;
                if (!Float.isFinite(span) || span <= 0) return;
                int rings = Math.min(MAX_TUBE_RINGS, (int) Math.ceil(span - 1.0E-4F));
                if (rings == MAX_TUBE_RINGS && System.currentTimeMillis() - lastCapLog > 1000) {
                    lastCapLog = System.currentTimeMillis();
                    LOG.warn("Tentacle sleeve of {} blocks capped at {}: a bad pose reached the renderer", span, MAX_TUBE_RINGS);
                }
                float top = Y1;
                for (int i = 0; i < rings; i++) {
                    float bottom = Math.max(Y0, top - 1);
                    float hv = (top - bottom) * 16;
                    sides(vc, p, s, light, X0, X1, bottom, top, Z0, Z1, 8 - w / 2, 8 + w / 2, 8 - d / 2, 8 + d / 2, 0, hv);
                    top = bottom;
                }
            }
        }
    }

    private static void caps(VertexConsumer vc, PoseStack.Pose p, TextureAtlasSprite s, int light, float X0, float X1, float Y0, float Y1, float Z0, float Z1,
                             float u0, float u1, float v0, float v1) {
        quad(vc, p, s, light, 0, 1, 0, u0, u1, v0, v1, X0, Y1, Z0, X1, Y1, Z0, X1, Y1, Z1, X0, Y1, Z1);
        quad(vc, p, s, light, 0, -1, 0, u0, u1, v0, v1, X0, Y0, Z1, X1, Y0, Z1, X1, Y0, Z0, X0, Y0, Z0);
    }

    private static void sides(VertexConsumer vc, PoseStack.Pose p, TextureAtlasSprite s, int light, float X0, float X1, float Y0, float Y1, float Z0, float Z1,
                              float uw0, float uw1, float ud0, float ud1, float v0, float v1) {
        quad(vc, p, s, light, 0, 0, -1, uw0, uw1, v0, v1, X1, Y1, Z0, X0, Y1, Z0, X0, Y0, Z0, X1, Y0, Z0);
        quad(vc, p, s, light, 0, 0, 1, uw0, uw1, v0, v1, X0, Y1, Z1, X1, Y1, Z1, X1, Y0, Z1, X0, Y0, Z1);
        quad(vc, p, s, light, -1, 0, 0, ud0, ud1, v0, v1, X0, Y1, Z0, X0, Y1, Z1, X0, Y0, Z1, X0, Y0, Z0);
        quad(vc, p, s, light, 1, 0, 0, ud0, ud1, v0, v1, X1, Y1, Z1, X1, Y1, Z0, X1, Y0, Z0, X1, Y0, Z1);
    }

    /** A cog disc in the YZ plane at x (px), diameter (px), both sides. */
    private static void decalX(PoseStack.Pose p, VertexConsumer vc, TextureAtlasSprite s, int light, Space space, float x, float diameter) {
        float X = x * PX, r = diameter / 2 * PX;
        if (space.nearCamera(p, X, 0, 0, 0, 0.5f)) return;
        quad(vc, p, s, light, -1, 0, 0, 0, 16, 0, 16, X, r, -r, X, r, r, X, -r, r, X, -r, -r);
        quad(vc, p, s, light, 1, 0, 0, 0, 16, 0, 16, X, r, r, X, r, -r, X, -r, -r, X, -r, r);
    }

    /** Sticker pad: slime face down (-Y, on the gripped face, lifted 0.004 against z-fighting), planks back, side strips. */
    private static void pad(PoseStack.Pose p, VertexConsumer vc, TextureAtlasSprite s, int light, Space space) {
        float r = 2.25f * PX, Y0 = 0.004f, Y1 = PAD;
        if (space.nearCamera(p, 0, 0, PAD, 0, 0.5f)) return;
        quad(vc, p, s, light, 0, -1, 0, 8.5f, 15.5f, 8.5f, 15.5f, -r, Y0, r, r, Y0, r, r, Y0, -r, -r, Y0, -r);
        quad(vc, p, s, light, 0, 1, 0, 0.5f, 7.5f, 8.5f, 15.5f, -r, Y1, -r, r, Y1, -r, r, Y1, r, -r, Y1, r);
        quad(vc, p, s, light, 0, 0, -1, 8.5f, 15.5f, 0, 2.0f, r, Y1, -r, -r, Y1, -r, -r, Y0, -r, r, Y0, -r);
        quad(vc, p, s, light, 0, 0, 1, 8.5f, 15.5f, 0, 2.0f, -r, Y1, r, r, Y1, r, r, Y0, r, -r, Y0, r);
        quad(vc, p, s, light, -1, 0, 0, 8.5f, 15.5f, 0, 2.0f, -r, Y1, -r, -r, Y1, r, -r, Y0, r, -r, Y0, -r);
        quad(vc, p, s, light, 1, 0, 0, 8.5f, 15.5f, 0, 2.0f, r, Y1, r, r, Y1, -r, r, Y0, -r, r, Y0, r);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, TextureAtlasSprite s, int light, float nx, float ny, float nz,
                             float uFrom, float uTo, float vFrom, float vTo,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        float u0 = s.getU(uFrom), u1 = s.getU(uTo);
        float v0 = s.getV(vFrom), v1 = s.getV(vTo);
        vertex(vc, p, ax, ay, az, u0, v0, light, nx, ny, nz);
        vertex(vc, p, dx, dy, dz, u0, v1, light, nx, ny, nz);
        vertex(vc, p, cx, cy, cz, u1, v1, light, nx, ny, nz);
        vertex(vc, p, bx, by, bz, u1, v0, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light, float nx, float ny, float nz) {
        vc.vertex(p.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(), nx, ny, nz).endVertex();
    }
}
