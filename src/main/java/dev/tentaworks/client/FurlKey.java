package dev.tentaworks.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.network.ActuatorFurlPacket;
import dev.tentaworks.rig.Actuators;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** The Furl key: asks the server to Furl or unfurl the worn Rig's Actuators; the server answers on the action bar. */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class FurlKey {
    private FurlKey() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean pressed = false;
        while (TentaworksKeys.FURL.consumeClick()) pressed = true;
        if (!pressed || player == null || mc.screen != null || !Actuators.fitted(player)) return;
        PacketDistributor.sendToServer(new ActuatorFurlPacket(!Actuators.furled(player)));
    }
}
