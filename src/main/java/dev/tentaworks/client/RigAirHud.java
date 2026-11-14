package dev.tentaworks.client;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.RigEnchants;
import dev.tentaworks.rig.TentacleRigItem;
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
 * an Actuator holding a Grab, or Air taken in the last {@value #RECENT} ticks (Far Use, Dual Wield, Throw; not a Diving Helmet's breath). Create's own overlay
 * shows it only for a Diving Helmet under water or in lava; then this one stays hidden, so it is never drawn twice.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class RigAirHud {
    /** Ticks the timer stays after the Rig's Air last went down, so a sip shows and slow drains do not blink. */
    static final int RECENT = 40;
    private static final String CREATE_VISUAL_AIR = "VisualBacktankAir";
    private static int lastAir = -1;
    private static long lastDrop = Long.MIN_VALUE / 2;
    private static long ticks;

    private RigAirHud() {
    }

    @SubscribeEvent
    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, TentaworksIds.id("rig_air"), RigAirHud::render);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        ticks++;
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack rig = player == null ? ItemStack.EMPTY : WornRig.of(player).stack();
        int air = rig.isEmpty() ? -1 : BacktankUtil.getAir(rig);
        if (air >= 0 && lastAir >= 0 && air < lastAir && !player.getPersistentData().contains(CREATE_VISUAL_AIR)) lastDrop = ticks;
        lastAir = air;
    }

    /** Whether the timer shows: the Rig is using Air and Create's diving timer is not already on screen. */
    public static boolean shown(LocalPlayer player) {
        if (player.isCreative() || player.isSpectator()) return false;
        ItemStack rig = WornRig.of(player).stack();
        if (!(rig.getItem() instanceof TentacleRigItem) || createShows(player)) return false;
        return draining(player, rig);
    }

    private static boolean draining(LocalPlayer player, ItemStack rig) {
        if (RigClient.state().drains()) return true;
        ActuatorClient.State hold = ActuatorClient.local();
        if ((hold.heldId() >= 0 || hold.ship() != null) && !RigEnchants.deft(rig)) return true;
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
