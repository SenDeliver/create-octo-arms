package dev.tentaworks.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.registry.TentaworksItems;
import dev.tentaworks.rig.Actuators;
import dev.tentaworks.rig.RigSettings;
import dev.tentaworks.rig.WornRig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * A small line left of the hotbar while the Actuators have something to say: what they hold (mob name or Ship, heavy) with the
 * Far Use mode glyph, or that they hang limp for lack of Air. A line too long for the room left of the hotbar (big GUI scale,
 * long mob names or translations) is drawn smaller. Nothing otherwise; refusals go to the action bar.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class ActuatorHud {
    private static final int GAP = 6, OFFHAND = 29, ICON = 18, EDGE = 2;

    private ActuatorHud() {
    }

    @SubscribeEvent
    static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, TentaworksIds.id("actuators"), ActuatorHud::render);
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
