package dev.tentaworks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.client.ActuatorClient.Affordance;
import dev.tentaworks.config.TentaworksClientConfig;
import dev.tentaworks.registry.TentaworksItems;
import dev.tentaworks.rig.Actuators;
import dev.tentaworks.mixin.GuiAccessor;
import dev.tentaworks.rig.Grab;
import dev.tentaworks.rig.GrabRule;
import dev.tentaworks.rig.Hold;
import dev.tentaworks.rig.HoldArm;
import dev.tentaworks.rig.RigSettings;
import dev.tentaworks.rig.RigTier;
import dev.tentaworks.rig.SetDown;
import dev.tentaworks.rig.TentacleRigItem;
import dev.tentaworks.rig.WornRig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import org.jetbrains.annotations.Nullable;

/**
 * What the Actuators tell the wearer on screen: marks only at the crosshair, words on the action bar and beside the hotbar.
 * <p>
 * Around the crosshair (first person, client config {@code hud.crosshair}) a bracket on each side, each one an Actuator (the left
 * bracket the left-side one). A free Actuator's bracket shows while it would do the action ({@link ActuatorSight.Mark}): the
 * acting side lit in the tier's colour, both for Dual Wield, hollow and faint just out of Reach, red when limp; it closes in with
 * every strike, and a click or a Grab key press on a target out of Reach flashes the hollow pair. A holding Actuator's bracket
 * is shut to a small solid pad tight against the crosshair ({@link #grips}), both for a two-Actuator hold; it snaps in when the
 * Grab lands ({@link #settle}) and goes pale over a spot the mob cannot be set down on (the in-world outline says where and
 * whether). While the Grab key is down with a hold a column of ticks beside that pad is the hold distance ({@link #gauge}): one
 * per notch of the wheel from the nearest hold up to Reach, the current one lit. Above the crosshair the Grab cue
 * ({@link ActuatorClient#affordance}): an open claw over a target a free Actuator can take, or a glyph for why not (two hands
 * needed, hands full, refused).
 * <p>
 * The keys are hints ({@link GrabHint}, client config {@code hud.hints}): the Grab key beside the open claw for the first second
 * of a look, and one action-bar line with the keys for Set Down, Throw and the hold distance when a Grab lands.
 * <p>
 * Left of the hotbar: a small line with what they hold (mob name or Ship, heavy) and the Far Use mode glyph, or that they hang
 * limp for lack of Air; drawn smaller when it does not fit.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class ActuatorHud {
    private static final int GAP = 6, OFFHAND = 29, ICON = 18, EDGE = 2;
    private static final ResourceLocation MARK_LEFT = sprite("mark_left"), MARK_RIGHT = sprite("mark_right"), HOLLOW_LEFT = sprite("mark_left_hollow"),
            HOLLOW_RIGHT = sprite("mark_right_hollow"), GRIP_LEFT = sprite("grip_left"), GRIP_RIGHT = sprite("grip_right"), CLAW_OPEN = sprite("claw_open"),
            TWO_HANDS = sprite("two_hands"), HANDS_FULL = sprite("hands_full"), REFUSED = sprite("refused");
    /** The mouse wheel in the hint line: the one glyph of the font {@code tentaworks:hud}. */
    private static final Component WHEEL = Component.literal("\uE000").withStyle(Style.EMPTY.withFont(TentaworksIds.id("hud")));
    /** Marks: size, gap to the crosshair's arms at rest and when the target is out of Reach. */
    static final int MARK_W = 7, MARK_H = 11;
    static final float MARK_GAP = 3, OUT_GAP = 6;
    /** A holding Actuator's pad: size, gap to the crosshair's arm, and the gap it snaps in from when the Grab lands. */
    static final int GRIP_W = 6, GRIP_H = 9;
    static final float GRIP_GAP = 1, GRIP_FROM = 6;
    /** The snap of a pad takes this long; it starts white and takes the tier's colour over the first ticks. */
    static final float SETTLE_TICKS = 8, SETTLE_FLASH = 5;
    /** A strike closes the marks in by {@link #CLOSE_PEAK} ticks and lets go by {@link #CLOSE_TICKS}: shorter than a mining swing, so they pulse. */
    static final float CLOSE_PEAK = 1.5F, CLOSE_TICKS = 3.5F;
    /** Opacity of the hollow marks at rest; a click out of Reach flashes them for this many ticks. */
    static final float OUT_ALPHA = 0.5F, MISS_TICKS = 14;
    /** The Grab key beside the open claw shows for this many ticks of a look and fades over the last ones. */
    static final int CAP_TICKS = 24, CAP_FADE = 6;
    /**
     * The hold distance gauge: the Grab key must be down longer than {@link #GAUGE_DELAY} ticks (a tap is a Set Down, no gauge
     * for it), it stays {@link #GAUGE_HOLD} ticks after the key or the wheel and is gone by {@link #GAUGE_TICKS}. A column of up
     * to {@link #GAUGE_ROOMY} notches has a gap between its ticks, a longer one (a long Reach) packs them.
     */
    public static final int GAUGE_DELAY = 3;
    static final float GAUGE_HOLD = 10, GAUGE_TICKS = 18;
    static final int GAUGE_ROOMY = 17;
    private static final int COPPER = 0xF7A35C, NETHERITE = 0xC9A2F5, RED = 0xFF4A4A, WHITE = 0xFFFFFF, PALE = 0x9C9CA4;
    private static final GrabHint HINT = new GrabHint();
    private static @Nullable Component hintLine;

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

    /** How far the marks have closed in on the crosshair {@code age} ticks after a swing started: 1 at {@link #CLOSE_PEAK}, back to 0 by {@link #CLOSE_TICKS}. */
    static float closeIn(float age) {
        if (age <= 0 || age >= CLOSE_TICKS) return 0;
        return age < CLOSE_PEAK ? age / CLOSE_PEAK : 1 - (age - CLOSE_PEAK) / (CLOSE_TICKS - CLOSE_PEAK);
    }

    /** The out-of-Reach flash {@code age} ticks after a click landed on nothing: full for the first half, gone by {@link #MISS_TICKS}. */
    static float miss(float age) {
        if (age < 0 || age >= MISS_TICKS) return 0;
        return Math.min(1, 2 * (1 - age / MISS_TICKS));
    }

    /** How far the flashing marks jump apart, 0..1: two quick beats that die out. */
    static float missShake(float age) {
        if (age < 0 || age >= MISS_TICKS) return 0;
        return (float) Math.abs(Math.sin(age / MISS_TICKS * 2 * Math.PI)) * (1 - age / MISS_TICKS);
    }

    /** The Grab key's opacity beside the open claw {@code age} ticks into a look at a grabbable target. */
    static float cueCap(float age) {
        if (age < 0 || age >= CAP_TICKS) return 0;
        return Math.min(1, (CAP_TICKS - age) / CAP_FADE);
    }

    /** Whether a side's mark shows: both sides for every mark but a plain reach, which shows the acting side only. */
    static boolean lit(ActuatorSight.Mark mark, boolean actingSide) {
        return mark != ActuatorSight.Mark.NONE && (mark != ActuatorSight.Mark.REACH || actingSide);
    }

    static boolean hollow(ActuatorSight.Mark mark) {
        return mark == ActuatorSight.Mark.OUT;
    }

    /** The bracket on the right ({@code right}) or the left is a holding Actuator's: the main-hand Actuator sits on the main-arm side. */
    static boolean grips(HoldArm hold, boolean rightHanded, boolean right) {
        return hold.uses(right == rightHanded ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    /** The side a plain reach lights: the main-hand Actuator's, or the other one's while the main-hand Actuator holds. */
    static boolean acts(HoldArm hold, boolean rightHanded, boolean right) {
        return (right == rightHanded) != (hold == HoldArm.MAIN);
    }

    /**
     * How far a pad still is from its grip {@code age} ticks after the Grab landed: 1 out at {@link #GRIP_FROM}, 0 home. It snaps
     * in within three ticks, squeezes a little past (below 0) and is home by {@link #SETTLE_TICKS}.
     */
    static float settle(float age) {
        if (age < 0 || age >= SETTLE_TICKS) return 0;
        float t = age / SETTLE_TICKS;
        return (1 - t) * (1 - t) * (float) Math.cos(t * 1.5 * Math.PI);
    }

    /** The gauge's opacity {@code age} ticks after the hold distance was last in the wearer's hands. */
    static float gauge(float age) {
        if (age < 0 || age >= GAUGE_TICKS) return 0;
        return Math.min(1, (GAUGE_TICKS - age) / (GAUGE_TICKS - GAUGE_HOLD));
    }

    /** Ticks of the gauge: one per {@link Hold#NOTCH} from the nearest hold {@code min} up to {@code max} (Reach), both ends counted. */
    static int gaugeNotches(double min, double max) {
        return Math.max(1, (int) Math.floor((max - min) / Hold.NOTCH + 1.0E-6) + 1);
    }

    /** The lit tick for a hold {@code distance} out, counted from the nearest one (0). */
    static int gaugeNotch(double distance, double min, int notches) {
        return Mth.clamp((int) Math.round((distance - min) / Hold.NOTCH), 0, notches - 1);
    }

    /** Pixels from one notch to the next: 2 (a gap between the ticks) while the column stays short, else 1 (every second notch drawn). */
    static int gaugePitch(int notches) {
        return notches <= GAUGE_ROOMY ? 2 : 1;
    }

    /** The crosshair marks show now: first person with a fitted Rig, not with the GUI hidden, under a screen or as a spectator, and not turned off. */
    public static boolean marksShown() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return player != null && !mc.options.hideGui && mc.screen == null && !player.isSpectator() && mc.options.getCameraType().isFirstPerson()
                && TentaworksClientConfig.crosshair() && Actuators.fitted(player);
    }

    /** The out-of-Reach flash is showing (a click just landed on nothing because its target was out of Reach). */
    public static boolean missShown(LocalPlayer player) {
        return marksShown() && miss(ActuatorSight.missedAge(player, 0)) > 0;
    }

    /** The brackets drawn as holding pads now: "L", "R", "LR" or "-" (dev trace). */
    public static String gripsShown(LocalPlayer player) {
        HoldArm hold = holdShown(player);
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        String out = (grips(hold, rightHanded, false) ? "L" : "") + (grips(hold, rightHanded, true) ? "R" : "");
        return !marksShown() || out.isEmpty() ? "-" : out;
    }

    /** The gauge as drawn now: {lit notch, notches}, or null when it does not show (dev trace). */
    public static int @Nullable [] gaugeShown(LocalPlayer player) {
        ActuatorClient.State state = ActuatorClient.get(player);
        if (!marksShown() || !state.holding() || gauge(ActuatorClient.gaugeAge(player, 0)) <= 0) return null;
        return gaugeOf(player, state);
    }

    /** The Grab key shows beside the open claw now (dev trace). */
    public static boolean capShown(LocalPlayer player) {
        return marksShown() && ActuatorSight.cue().kind() == Affordance.Kind.GRAB && HINT.live(TentaworksClientConfig.hints())
                && cueCap(ActuatorSight.cueAge(player)) > 0;
    }

    /** The session's hints (dev trace, and the dev script starts them over). */
    public static GrabHint hints() {
        return HINT;
    }

    /** The action-bar line now showing, or null. */
    public static @Nullable Component actionBar() {
        GuiAccessor gui = (GuiAccessor) Minecraft.getInstance().gui;
        return gui.tentaworks$overlayMessageTime() > 0 ? gui.tentaworks$overlayMessage() : null;
    }

    /** The local wearer's Grab landed: the controls hint goes up on the action bar ({@link GrabHint}). */
    static void grabbed(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        if (!HINT.landed(mc.gui.getGuiTicks(), TentaworksClientConfig.hints(), actionBar() != null)) return;
        hintLine = hintLine(TentaworksKeys.GRAB.getTranslatedKeyMessage(), mc.options.keyAttack.getTranslatedKeyMessage(),
                GrabRule.throwsOnAttack(player.getMainHandItem().isEmpty(), false));
        mc.gui.setOverlayMessage(hintLine, false);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (hintLine == null || mc.player == null) return;
        if (HINT.renews(mc.gui.getGuiTicks(), ActuatorClient.get(mc.player).holding(), actionBar() == hintLine)) mc.gui.setOverlayMessage(hintLine, false);
    }

    /** The holding arm the brackets show: none while limp (the red pair says so, the hold is about to end). */
    private static HoldArm holdShown(LocalPlayer player) {
        ActuatorClient.State state = ActuatorClient.get(player);
        return state.holding() && ActuatorSight.mark() != ActuatorSight.Mark.LIMP ? state.arm() : HoldArm.NONE;
    }

    private static int[] gaugeOf(LocalPlayer player, ActuatorClient.State state) {
        double reach = TentacleRigItem.reach(player);
        Entity held = ActuatorClient.held(player);
        double min = held != null ? Hold.minDistance(held.getBbWidth(), 0) : Hold.SHIP_MIN;
        int notches = gaugeNotches(min, held != null ? reach : reach - Grab.SHIP_INSIDE);
        return new int[] {gaugeNotch(state.distance(), min, notches), notches};
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
        ActuatorClient.State state = ActuatorClient.get(player);
        ActuatorSight.Mark mark = ActuatorSight.mark();
        HoldArm hold = holdShown(player);
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        float missAge = ActuatorSight.missedAge(player, pt);
        float miss = mark == ActuatorSight.Mark.NONE || hollow(mark) ? miss(missAge) : 0;
        boolean hollow = miss > 0 || hollow(mark);
        float heldAge = ActuatorClient.heldAge(player, pt);
        SetDown.Spot spot = ActuatorClient.setDownSpot();
        int gripColor = mix(spot != null && !spot.valid() ? mix(tint, PALE, 0.7F) : tint, WHITE, Mth.clamp(1 - heldAge / SETTLE_FLASH, 0, 1));
        float gripGap = GRIP_GAP + (GRIP_FROM - GRIP_GAP) * settle(heldAge);
        for (int side = 0; side < 2; side++) {
            boolean right = side == 1;
            if (grips(hold, rightHanded, right)) {
                float x = right ? cx + 4 + gripGap : cx - 5 - gripGap - GRIP_W;
                blit(g, right ? GRIP_RIGHT : GRIP_LEFT, x, cy - 0.5F - GRIP_H / 2F, GRIP_W, GRIP_H, gripColor, 1);
            } else if (hollow || lit(mark, acts(hold, rightHanded, right))) {
                int color = hollow ? WHITE : mark == ActuatorSight.Mark.LIMP ? RED : tint;
                float gap = hollow ? OUT_GAP + (miss > 0 ? 3 * missShake(missAge) : 0)
                        : MARK_GAP * (1 - (mark == ActuatorSight.Mark.LIMP ? 0 : closeIn(ActuatorSight.firedAge(player, pt))));
                float x = right ? cx + 4 + gap : cx - 5 - gap - MARK_W;
                blit(g, hollow ? right ? HOLLOW_RIGHT : HOLLOW_LEFT : right ? MARK_RIGHT : MARK_LEFT, x, cy - 0.5F - MARK_H / 2F, MARK_W, MARK_H, color,
                        hollow ? Math.max(OUT_ALPHA, miss) : 1);
            }
        }
        if (state.holding()) {
            float alpha = gauge(ActuatorClient.gaugeAge(player, pt));
            if (alpha > 0 && hold.holding()) {
                int[] at = gaugeOf(player, state);
                boolean right = !grips(hold, rightHanded, false);
                int out = (int) GRIP_GAP + GRIP_W + 3;
                gauge(g, right ? cx + 4 + out : cx - 5 - out, cy, right, at[0], at[1], tint, alpha);
            }
        } else {
            Affordance cue = ActuatorSight.cue();
            float age = ActuatorSight.cueAge(player) + pt;
            int top = cy - 20;
            switch (cue.kind()) {
                case GRAB -> {
                    float pop = 1 - Mth.clamp(age / 3F, 0, 1);
                    blit(g, CLAW_OPEN, cx - 5.5F, top + 1 - 2 * pop, 11, 9, tint, 1);
                    float cap = HINT.live(TentaworksClientConfig.hints()) ? cueCap(age) : 0;
                    if (cap > 0) keyCap(g, mc, TentaworksKeys.GRAB.getTranslatedKeyMessage(), cx + 9, top + 1, cap);
                }
                case TWO_HANDS -> blit(g, TWO_HANDS, cx - 6.5F, top + 3, 13, 7, WHITE, 1);
                case HANDS_FULL -> blit(g, HANDS_FULL, cx - 5.5F, top, 11, 10, WHITE, 1);
                case REFUSED -> blit(g, REFUSED, cx - 5.5F, top, 11, 10, WHITE, 1);
                default -> {
                }
            }
        }
        g.setColor(1, 1, 1, 1);
    }

    /**
     * The hold distance gauge: a column of ticks, the nearest hold at the bottom, on the outer side of the pad at {@code x}
     * (its edge toward the crosshair); the lit one is wider and in the tier's colour.
     */
    private static void gauge(GuiGraphics g, int x, int cy, boolean right, int lit, int notches, int tint, float alpha) {
        int pitch = gaugePitch(notches), height = (notches - 1) * pitch + 1;
        int bottom = cy - 1 + (height - 1) / 2, x0 = right ? x : x - 2, a = Math.round(alpha * 255);
        g.fill(x0 - 1, bottom - height, x0 + 3, bottom + 2, Math.round(alpha * 110) << 24 | 0x101014);
        for (int i = 0; i < notches; i++) {
            if (i * pitch % 2 != 0) continue;
            g.fill(x0, bottom - i * pitch, x0 + 2, bottom - i * pitch + 1, Math.max(4, Math.round(alpha * 150)) << 24 | 0xE6E6E6);
        }
        int y = bottom - lit * pitch;
        g.fill(x0 - 2, y - 1, x0 + 4, y + 2, Math.max(4, Math.round(alpha * 235)) << 24 | 0x121012);
        g.fill(x0 - 1, y, x0 + 3, y + 1, Math.max(4, a) << 24 | tint);
    }

    private static int mix(int from, int to, float k) {
        int r = Math.round(Mth.lerp(k, from >> 16 & 0xFF, to >> 16 & 0xFF)), gr = Math.round(Mth.lerp(k, from >> 8 & 0xFF, to >> 8 & 0xFF)),
                b = Math.round(Mth.lerp(k, from & 0xFF, to & 0xFF));
        return r << 16 | gr << 8 | b;
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

    /** A key's name on a dark cap. */
    private static void keyCap(GuiGraphics g, Minecraft mc, Component keys, int x, int y, float alpha) {
        int w = mc.font.width(keys) + 4;
        g.fill(x, y - 2, x + w, y + 10, Math.round(alpha * 170) << 24 | 0x101014);
        g.drawString(mc.font, keys, x + 2, y, Math.max(4, Math.round(alpha * 255)) << 24 | WHITE, false);
    }

    /**
     * The controls of a hold as one action-bar line: each key's bound name, then what it does; the wheel is a glyph.
     * {@code plainAttackThrows}: {@link GrabRule#throwsOnAttack} with the Grab key up.
     */
    static Component hintLine(Component grab, Component attack, boolean plainAttackThrows) {
        return Component.empty().append(hint(grab, "set_down")).append("   ").append(hint(throwKeys(grab, attack, plainAttackThrows), "throw")).append("   ")
                .append(hint(Component.empty().append(grab).append(" + ").append(WHEEL), "distance"));
    }

    private static Component hint(Component keys, String what) {
        return Component.empty().append(keys).append(" ").append(Component.translatable(key(what)).withStyle(ChatFormatting.GRAY));
    }

    /** The keys of a Throw: attack alone when a plain attack Throws ({@link GrabRule#throwsOnAttack}), else the Grab key with it. */
    static Component throwKeys(Component grab, Component attack, boolean plainAttackThrows) {
        return plainAttackThrows ? attack : Component.empty().append(grab).append(" + ").append(attack);
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
