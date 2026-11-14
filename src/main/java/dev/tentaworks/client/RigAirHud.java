package dev.tentaworks.client;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.RigEnchants;
import dev.tentaworks.rig.WornRig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Create's remaining-Air timer (Backtank icon and time beside the hotbar) for the worn Rig while it uses Air: Active or Setting Down,
 * an Actuator holding a Grab (not with Deft), a Far Use reach, or Air taken in the last {@value #RECENT} ticks (Dual Wield, Throw, Create tools that draw
 * Air from the Rig; not a Diving Helmet's breath). Create's own overlay
 * shows it only for a Diving Helmet under water or in lava; then this one stays hidden, so it is never drawn twice.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class RigAirHud {
    /** Ticks the timer stays after the Rig's Air last went down, so a sip shows and slow drains do not blink. */
    static final int RECENT = 40;
    /** Ticks after a Diving Helmet last breathed in which a drop is still its breath (the server's sip can land after the eyes leave the water). */
    static final int HELMET_GRACE = 20;
    private static final String CREATE_VISUAL_AIR = "VisualBacktankAir";
    private static WornRig lastRig = WornRig.NONE;
    private static int lastAir = -1;
    private static long lastDrop = Long.MIN_VALUE / 2;
    private static long lastHelmet = Long.MIN_VALUE / 2;
    private static long ticks;

    private RigAirHud() {
    }

    @SubscribeEvent
    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, TentaworksIds.id("rig_air"), RigAirHud::render);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        ticks++;
        LocalPlayer player = Minecraft.getInstance().player;
        WornRig worn = player == null ? WornRig.NONE : WornRig.of(player);
        ItemStack rig = worn.stack();
        int air = rig.isEmpty() ? -1 : BacktankUtil.getAir(rig);
        if (player != null && player.getPersistentData().contains(CREATE_VISUAL_AIR)) lastHelmet = ticks;
        boolean sameRig = WornRig.same(lastRig, worn);
        if (sameRig && air >= 0 && lastAir >= 0 && air < lastAir && ticks - lastHelmet > HELMET_GRACE) lastDrop = ticks;
        if (!rig.isEmpty() && ActuatorClient.local().farTtl() > 0 && !RigEnchants.deft(rig)) lastDrop = ticks;
        lastRig = worn;
        lastAir = air;
    }

    /** Whether the timer shows: the Rig is using Air and Create's diving timer is not already on screen. */
    public static boolean shown(LocalPlayer player) {
        if (player.isCreative() || player.isSpectator()) return false;
        ItemStack rig = WornRig.of(player).stack();
        if (rig.isEmpty() || createShows(player)) return false;
        return draining(player, rig);
    }

    private static boolean draining(LocalPlayer player, ItemStack rig) {
        if (RigClient.state().drains()) return true;
        ActuatorClient.State hold = ActuatorClient.local();
        if (hold.holding() && !RigEnchants.deft(rig)) return true;
        return ticks - lastDrop <= RECENT;
    }

    /** Create's {@code RemainingAirOverlay} condition: a Diving Helmet breathing from a Backtank, eyes in a fluid it would drown in, or in lava. */
    private static boolean createShows(LocalPlayer player) {
        if (!player.getPersistentData().contains(CREATE_VISUAL_AIR)) return false;
        boolean isAir = player.getEyeInFluidType().isAir()
                || player.level().getBlockState(BlockPos.containing(player.getX(), player.getEyeY(), player.getZ())).is(Blocks.BUBBLE_COLUMN);
        boolean canBreathe = !player.canDrownInFluidType(player.getEyeInFluidType()) || MobEffectUtil.hasWaterBreathing(player)
                || player.getAbilities().invulnerable;
        return !(isAir || canBreathe) || player.isInLava();
    }

    /** Drawn as Create draws it: the Rig at the right of the hotbar and its Air as a time (1 Air a second, Create's diving rate). */
    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || !shown(player)) return;
        ItemStack rig = WornRig.of(player).stack();
        int air = BacktankUtil.getAir(rig);
        g.pose().pushPose();
        g.pose().translate(g.guiWidth() / 2 + 90, g.guiHeight() - 53 + (rig.has(DataComponents.FIRE_RESISTANT) ? 9 : 0), 0);
        g.renderItem(rig, 0, 0);
        int color = 0xFFFFFFFF;
        if (air < 60 && air % 2 == 0) color = mix(0xFFFF0000, color, Math.max(air / 60f, .25f));
        g.drawString(mc.font, StringUtil.formatTickDuration(Math.max(0, air - 1) * 20, mc.level.tickRateManager().tickrate()), 16, 5, color);
        g.pose().popPose();
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF));
        int gr = Math.round(Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }
}
