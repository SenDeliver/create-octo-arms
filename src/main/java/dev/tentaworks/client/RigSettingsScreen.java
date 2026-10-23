package dev.tentaworks.client;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.network.RigSettingsPacket;
import dev.tentaworks.network.TentaworksNetwork;
import dev.tentaworks.rig.RigSettings;
import dev.tentaworks.rig.RigSettings.ActivationMode;
import dev.tentaworks.rig.TentacleRigItem;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Rig Settings (design §7), Create symmetry-wand style: scroll the rows, the values are written to the Rig on close.
 * The panel is Create's wand panel, stretched to three rows and a wider field.
 */
public class RigSettingsScreen extends AbstractSimiScreen {
    private static final AllGuiTextures PANEL = AllGuiTextures.WAND_OF_SYMMETRY;
    private static final int EXTRA = 56;
    private static final int W = 188 + EXTRA;
    private static final int H = 127;
    private static final int ROW = 22;
    private static final int TITLE_COLOR = 0x592424;

    private final EquipmentSlot slot;
    private final ItemStack rig;
    private final double reach;
    private int height;
    private int speed;
    private ActivationMode mode;

    public RigSettingsScreen(EquipmentSlot slot, ItemStack rig) {
        super(Component.translatable(key("title"), rig.getHoverName()));
        this.slot = slot;
        this.rig = rig;
        this.reach = rig.getItem() instanceof TentacleRigItem item ? item.reach(rig) : 0;
        RigSettings s = RigSettings.of(rig);
        height = Math.round(s.standingHeight() * 10);
        speed = Math.round(s.speed() * 10);
        mode = s.activation();
    }

    public static void openInHand(InteractionHand hand) {
        open(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }

    public static void open(EquipmentSlot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getItemBySlot(slot);
        if (stack.getItem() instanceof TentacleRigItem) ScreenOpener.open(new RigSettingsScreen(slot, stack));
    }

    static String key(String name) {
        return TentaworksIds.MOD_ID + ".gui.rig_settings." + name;
    }

    static String tenths(int v) {
        return String.format(Locale.ROOT, "%.1f", v / 10.0);
    }

    public RigSettings settings() {
        return new RigSettings(height / 10.0F, speed / 10.0F, mode).clamped(reach);
    }

    @Override
    protected void init() {
        setWindowSize(W, H);
        setWindowOffset(-20, 0);
        super.init();
        int x = guiLeft, y = guiTop;
        addRenderableWidget(new ScrollInput(x + 43, y + 23, 111 + EXTRA, 18)
                .withRange(0, (int) Math.floor(reach * 10) + 1)
                .withShiftStep(5)
                .titled(Component.translatable(key("standing_height")))
                .addHint(Component.translatable(key("standing_height.hint")))
                .calling(v -> height = v)
                .setState(height));
        addRenderableWidget(new ScrollInput(x + 43, y + 23 + ROW, 111 + EXTRA, 18)
                .withRange(Math.round(RigSettings.minSpeed() * 10), Math.round(RigSettings.maxSpeed() * 10) + 1)
                .withShiftStep(5)
                .titled(Component.translatable(key("speed")))
                .addHint(Component.translatable(key("speed.hint")))
                .calling(v -> speed = v)
                .setState(speed));
        addRenderableWidget(new SelectionScrollInput(x + 43, y + 23 + 2 * ROW, 111 + EXTRA, 18)
                .forOptions(List.of(modeName(ActivationMode.DOUBLE_JUMP), modeName(ActivationMode.KEY)))
                .titled(Component.translatable(key("activation")))
                .calling(v -> mode = ActivationMode.values()[v])
                .setState(mode.ordinal()));
        IconButton confirm = new IconButton(x + W - 33, y + H - 24, AllIcons.I_CONFIRM);
        confirm.withCallback(this::onClose);
        addRenderableWidget(confirm);
    }

    private static MutableComponent modeName(ActivationMode m) {
        return Component.translatable(key("activation." + m.getSerializedName()));
    }

    @Override
    protected void renderWindow(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        int x = guiLeft, y = guiTop;
        band(g, x, y, 0, 45, 0);
        band(g, x, y, 23, 45, 45);
        band(g, x, y, 23, 45, 67);
        band(g, x, y, 63, 101, 89);
        g.drawString(font, title, x + (W - font.width(title)) / 2, y + 4, TITLE_COLOR, false);

        row(g, x, y, 0, AllIcons.I_TOOL_MOVE_Y, Component.translatable(key("standing_height")),
                Component.translatable(key("blocks"), tenths(height)));
        row(g, x, y, 1, AllIcons.I_PRIORITY_VERY_HIGH, Component.translatable(key("speed")),
                Component.translatable(key("blocks_per_second"), tenths(speed)));
        row(g, x, y, 2, AllIcons.I_ACTIVE, Component.translatable(key("activation")), modeName(mode));

        int hintColor = AllGuiTextures.FONT_COLOR;
        g.drawString(font, Component.translatable(key("limits"), tenths((int) Math.floor(reach * 10)), tenths((int) Math.round(TentacleRigItem.topSpeed(rig) * 10))),
                x + 8, y + 102, hintColor, false);
        Component second = mode == ActivationMode.KEY
                ? (TentaworksKeys.TOGGLE.isUnbound() ? Component.translatable(key("toggle_unbound"))
                : Component.translatable(key("toggle_key"), TentaworksKeys.TOGGLE.getTranslatedKeyMessage()))
                : Component.translatable(key("double_jump_hint"));
        g.drawString(font, second, x + 8, y + 113, hintColor, false);

        GuiGameElement.of(rig).<GuiGameElement.GuiRenderBuilder>at(x + W + 6, y + H - 56, 100).scale(5).render(g);
    }

    private void band(GuiGraphics g, int x, int y, int srcY0, int srcY1, int destY) {
        int u = PANEL.getStartX(), v = PANEL.getStartY() + srcY0, h = srcY1 - srcY0;
        g.blit(PANEL.location, x, y + destY, u, v, 100, h);
        g.blit(PANEL.location, x + 100, y + destY, u + 60, v, EXTRA, h);
        g.blit(PANEL.location, x + 100 + EXTRA, y + destY, u + 100, v, 88, h);
    }

    private void row(GuiGraphics g, int x, int y, int row, AllIcons icon, Component name, Component value) {
        int top = y + ROW * row;
        icon.render(g, x + 22, top + 24);
        g.drawString(font, name, x + 51, top + 28, 0xDDDDDD, true);
        g.drawString(font, value, x + 144 + EXTRA - font.width(value), top + 28, 0xFFFFFF, true);
    }

    @Override
    public void removed() {
        RigSettings s = settings();
        RigSettings.set(rig, s);
        TentaworksNetwork.sendToServer(new RigSettingsPacket(slot, s));
    }
}
