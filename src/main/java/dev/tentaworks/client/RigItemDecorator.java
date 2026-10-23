package dev.tentaworks.client;

import dev.tentaworks.registry.TentaworksItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;

/**
 * Second item bar for a Rig: the Backtank bar below shows Air, this one above it shows durability
 * (vanilla green-to-red, drawn only once the Rig is damaged, like any armor).
 */
public final class RigItemDecorator implements IItemDecorator {
    private RigItemDecorator() {}

    public static void register(RegisterItemDecorationsEvent event) {
        RigItemDecorator decorator = new RigItemDecorator();
        event.register(TentaworksItems.COPPER_TENTACLE_RIG.get(), decorator);
        event.register(TentaworksItems.NETHERITE_TENTACLE_RIG.get(), decorator);
    }

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!stack.isDamaged()) return false;
        int max = stack.getMaxDamage();
        float left = Math.max(0.0F, (float) (max - stack.getDamageValue()) / max);
        int width = Math.round(left * 13.0F);
        int color = Mth.hsvToRgb(left / 3.0F, 1.0F, 1.0F);
        int bx = x + 2;
        int by = y + 11;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 200.0F);
        graphics.fill(RenderType.guiOverlay(), bx, by, bx + 13, by + 2, 0xFF000000);
        graphics.fill(RenderType.guiOverlay(), bx, by, bx + width, by + 1, color | 0xFF000000);
        graphics.pose().popPose();
        return false;
    }
}
