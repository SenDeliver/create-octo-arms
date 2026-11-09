package dev.tentaworks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.config.TentaworksClientConfig;
import dev.tentaworks.registry.TentaworksItems;
import dev.tentaworks.rig.Actuators;
import dev.tentaworks.rig.RigSettings;
import dev.tentaworks.rig.RigTier;
import dev.tentaworks.rig.TentacleRigItem;
import dev.tentaworks.rig.WornRig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * What the Actuators tell the wearer on screen.
 * <p>
 * Around the crosshair (first person, client config {@code hud.crosshair}): a claw bracket on each side while an Actuator would
 * do the action ({@link ActuatorSight.Mark}): the acting side lit in the tier's colour, both for Dual Wield, hollow grey just
 * out of Reach, red when limp; they close in for a moment when the action fires. Above it the Grab cue: an open claw with the
 * Grab key's name over a target a free Actuator can take, or why not (two hands needed, hands full, refused); a closed claw
 * while holding, with the set-down marker (red when the spot is not free), the hold distance while the Grab key is down, and
 * for the first seconds a row of hints below.
 * <p>
 * Left of the hotbar: a small line with what they hold (mob name or Ship, heavy) and the Far Use mode glyph, or that they hang
 * limp for lack of Air; drawn smaller when it does not fit.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class ActuatorHud {
    private static final int GAP = 6, OFFHAND = 29, ICON = 18, EDGE = 2;
    private static final ResourceLocation MARK_LEFT = sprite("mark_left"), MARK_RIGHT = sprite("mark_right"), HOLLOW_LEFT = sprite("mark_left_hollow"),
            HOLLOW_RIGHT = sprite("mark_right_hollow"), CLAW_OPEN = sprite("claw_open"), CLAW_CLOSED = sprite("claw_closed"),
            TWO_HANDS = sprite("two_hands"), HANDS_FULL = sprite("hands_full"), REFUSED = sprite("refused"), SET_DOWN = sprite("set_down"),
            SCROLL = sprite("scroll");
    /** Marks: size, gap to the crosshair's arms at rest and when the target is out of Reach, ticks the close-in lasts. */
    static final int MARK_W = 7, MARK_H = 11;
    static final float MARK_GAP = 3, OUT_GAP = 6, CLOSE_TICKS = 5;
    /** The hint row shows for this many ticks of a hold and fades over the last ones. */
    static final int HINT_TICKS = 100, HINT_FADE = 12;
    private static final int COPPER = 0xF7A35C, NETHERITE = 0xC9A2F5, RED = 0xFF4A4A, WHITE = 0xFFFFFF;

    private ActuatorHud() {
    }

    @SubscribeEvent
    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, TentaworksIds.id("actuators"), ActuatorHud::render);
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, TentaworksIds.id("actuator_marks"), ActuatorHud::renderMarks);
    }

    private static ResourceLocation sprite(String name) {
        return TentaworksIds.id("hud/" + name);
    }

    /** How far the marks have closed in on the crosshair {@code age} ticks after an action fired: 1 at one tick, back to 0 by {@link #CLOSE_TICKS}. */
    static float closeIn(float age) {
        if (age <= 0 || age >= CLOSE_TICKS) return 0;
        return age < 1 ? age : 1 - (age - 1) / (CLOSE_TICKS - 1);
    }

    /** Whether a side's mark shows: both sides for every mark but a plain reach, which shows the acting side only. */
    static boolean lit(ActuatorSight.Mark mark, boolean actingSide) {
        return mark != ActuatorSight.Mark.NONE && (mark != ActuatorSight.Mark.REACH || actingSide);
    }

    static boolean hollow(ActuatorSight.Mark mark) {
        return mark == ActuatorSight.Mark.OUT;
    }

    /** The hint row's opacity {@code age} ticks into a hold. */
    static float hint(float age) {
        if (age < 0 || age >= HINT_TICKS) return 0;
        return Math.min(1, (HINT_TICKS - age) / HINT_FADE);
    }

    /** The crosshair marks show now: first person with a fitted Rig, not with the GUI hidden, under a screen or as a spectator, and not turned off. */
    public static boolean marksShown() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return player != null && !mc.options.hideGui && mc.screen == null && !player.isSpectator() && mc.options.getCameraType().isFirstPerson()
                && TentaworksClientConfig.crosshair() && Actuators.fitted(player);
    }

    private static void renderMarks(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!marksShown()) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int tint = WornRig.of(player).item() instanceof TentacleRigItem rig && rig.tier() == RigTier.NETHERITE ? NETHERITE : COPPER;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ActuatorSight.Mark mark = ActuatorSight.mark();
        if (mark != ActuatorSight.Mark.NONE) {
            boolean hollow = hollow(mark), rightActs = player.getMainArm() == HumanoidArm.RIGHT;
            int color = hollow ? WHITE : mark == ActuatorSight.Mark.LIMP ? RED : tint;
            float gap = hollow ? OUT_GAP : MARK_GAP * (1 - (mark == ActuatorSight.Mark.LIMP ? 0 : closeIn(ActuatorSight.firedAge(player, pt))));
            float y = cy - 0.5F - MARK_H / 2F;
            if (lit(mark, !rightActs)) blit(g, hollow ? HOLLOW_LEFT : MARK_LEFT, cx - 5 - gap - MARK_W, y, MARK_W, MARK_H, color, 1);
            if (lit(mark, rightActs)) blit(g, hollow ? HOLLOW_RIGHT : MARK_RIGHT, cx + 4 + gap, y, MARK_W, MARK_H, color, 1);
        }
        ActuatorView.HoldArm hold = ActuatorView.holdArm(player);
        int top = cy - 20;
        if (hold == ActuatorView.HoldArm.NONE) {
            ActuatorView.Cue cue = ActuatorSight.cue();
            if (cue.grab()) {
                float pop = 1 - Mth.clamp((ActuatorSight.cueAge(player) + pt) / 3F, 0, 1);
                blit(g, CLAW_OPEN, cx - 5.5F, top + 1 - 2 * pop, 11, 9, tint, 1);
                keyCap(g, mc, TentaworksKeys.GRAB.getTranslatedKeyMessage(), cx + 9, top + 1, 1);
            } else if (cue == ActuatorView.Cue.TWO_HANDS) {
                blit(g, TWO_HANDS, cx - 6.5F, top + 3, 13, 7, WHITE, 1);
            } else if (cue == ActuatorView.Cue.HANDS_FULL) {
                blit(g, HANDS_FULL, cx - 5.5F, top, 11, 10, WHITE, 1);
            } else if (cue == ActuatorView.Cue.REFUSED) {
                blit(g, REFUSED, cx - 5.5F, top, 11, 10, WHITE, 1);
            }
        } else {
            blit(g, CLAW_CLOSED, cx - 5.5F, top + 1, 11, 9, tint, 1);
            ActuatorView.Spot spot = ActuatorView.setDownSpot(player);
            if (spot != null) blit(g, SET_DOWN, cx + 8, top + 1, 7, 8, spot.valid() ? tint : RED, 1);
            if (ActuatorView.grabKeyDown()) {
                double distance = ActuatorView.holdDistance(player);
                String text = distance > 0 ? String.format(java.util.Locale.ROOT, "%.1f", distance) : "";
                int w = mc.font.width(text);
                blit(g, SCROLL, cx - 8 - 7, top, 7, 11, WHITE, 1);
                if (w > 0) g.drawString(mc.font, text, cx - 8 - 9 - w, top + 2, WHITE, true);
            }
            ActuatorRenderer.Anim anim = ActuatorRenderer.anim(player);
            float alpha = anim == null ? 0 : hint(player.tickCount + pt - anim.grabStart);
            if (alpha > 0) hints(g, mc, player, cx, cy + 18, alpha);
        }
        g.setColor(1, 1, 1, 1);
    }

    private static void blit(GuiGraphics g, ResourceLocation sprite, float x, float y, int w, int h, int rgb, float alpha) {
        if (alpha <= 0) return;
        g.setColor((rgb >> 16 & 0xFF) / 255F, (rgb >> 8 & 0xFF) / 255F, (rgb & 0xFF) / 255F, alpha);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.blitSprite(sprite, 0, 0, w, h);
        g.pose().popPose();
        g.setColor(1, 1, 1, 1);
    }

    /** A key's name on a dark cap; returns the cap's width. */
    private static int keyCap(GuiGraphics g, Minecraft mc, Component keys, int x, int y, float alpha) {
        int w = mc.font.width(keys) + 4;
        g.fill(x, y - 2, x + w, y + 10, Math.round(alpha * 170) << 24 | 0x101014);
        g.drawString(mc.font, keys, x + 2, y, Math.max(4, Math.round(alpha * 255)) << 24 | WHITE, false);
        return w;
    }

    /** One hint: the keys in a cap, the scroll wheel after them when {@code scroll}, then what they do. */
    private record Hint(Component keys, boolean scroll, Component label) {
        int width(Minecraft mc) {
            return mc.font.width(keys) + 5 + (scroll ? 9 : 0) + 3 + mc.font.width(label);
        }
    }

    private static void hints(GuiGraphics g, Minecraft mc, LocalPlayer player, int cx, int y, float alpha) {
        Component grab = TentaworksKeys.GRAB.getTranslatedKeyMessage(), attack = mc.options.keyAttack.getTranslatedKeyMessage();
        Component thrower = ActuatorView.throwNeedsGrabKey(player) ? Component.empty().append(grab).append(" + ").append(attack) : attack;
        Hint[] row = {new Hint(grab, false, Component.translatableWithFallback(key("set_down"), "Set Down")),
                new Hint(thrower, false, Component.translatableWithFallback(key("throw"), "Throw")),
                new Hint(grab, true, Component.translatableWithFallback(key("distance"), "Distance"))};
        int between = 10, width = -between;
        for (Hint h : row) width += h.width(mc) + between;
        float scale = Math.min(1, (g.guiWidth() - 8) / (float) width);
        int a = Math.max(4, Math.round(alpha * 255));
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1);
        int x = -width / 2;
        for (Hint h : row) {
            x += keyCap(g, mc, h.keys(), x, 0, alpha) + 1;
            if (h.scroll()) {
                blit(g, SCROLL, x + 1, -1, 7, 11, WHITE, alpha);
                x += 9;
            }
            g.drawString(mc.font, h.label(), x + 3, 0, a << 24 | 0xE0E0E0, true);
            x += 3 + mc.font.width(h.label()) + between;
        }
        g.pose().popPose();
    }

    static String key(String name) {
        return TentaworksIds.MOD_ID + ".hud." + name;
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator() || !Actuators.fitted(player)) return;
        Component line = line(mc, player);
        if (line == null) return;
        int right = g.guiWidth() / 2 - 91 - GAP;
        if (!player.getOffhandItem().isEmpty() && player.getMainArm() == HumanoidArm.RIGHT) right -= OFFHAND;
        int y = g.guiHeight() - 19;
        int width = mc.font.width(line);
        float scale = Math.min(1f, (right - ICON - EDGE) / (float) width);
        int x = right - Math.round(width * scale);
        g.pose().pushPose();
        g.pose().translate(x, y + 4 + 4 * (1 - scale), 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(mc.font, line, 0, 0, 0xFFFFFF, true);
        g.pose().popPose();
        int icon = x - ICON;
        g.renderItem(TentaworksItems.ACTUATOR.asStack(), icon, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.drawString(mc.font, farUseGlyph(player), icon + 11, y + 9, 0xFFFFFF, true);
        g.pose().popPose();
    }

    private static Component line(Minecraft mc, LocalPlayer player) {
        if (Actuators.limp(player)) return Component.translatable(key("no_air")).withStyle(ChatFormatting.RED);
        ActuatorClient.State s = ActuatorClient.get(player);
        if (!s.holding()) return null;
        Component name;
        if (s.ship() != null) name = Component.translatable(key("ship"));
        else {
            Entity held = mc.level == null ? null : mc.level.getEntity(s.heldId());
            if (held == null) return null;
            name = held.getDisplayName();
        }
        MutableComponent out = Component.translatable(key("holding"), name);
        if (s.heavy() || s.ship() != null) out.append(Component.literal(" ")).append(Component.translatable(key("heavy")).withStyle(ChatFormatting.GRAY));
        return out;
    }

    /** Far Use: green always, yellow with the Rig out only, grey off or paused by a heavy hold. */
    private static Component farUseGlyph(LocalPlayer player) {
        RigSettings.FarUseMode mode = RigSettings.of(WornRig.of(player).stack()).farUse();
        ActuatorClient.State s = ActuatorClient.get(player);
        boolean paused = s.holding() && (s.heavy() || s.ship() != null);
        ChatFormatting color = paused || mode == RigSettings.FarUseMode.OFF ? ChatFormatting.DARK_GRAY
                : mode == RigSettings.FarUseMode.RIG_OUT ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
        return Component.literal(mode == RigSettings.FarUseMode.OFF ? "x" : "»").withStyle(color);
    }
}
