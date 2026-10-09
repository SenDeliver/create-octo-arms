package dev.tentaworks.dev.client;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.client.RigClient;
import dev.tentaworks.client.RigSettingsScreen;
import dev.tentaworks.network.RigSettingsPacket;
import dev.tentaworks.registry.TentaworksDataComponents;
import dev.tentaworks.rig.RigSettings;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.item.ItemDescription;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.Arrays;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.fml.ModList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import com.simibubi.create.content.equipment.armor.BacktankRenderer;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.minecraft.world.entity.EquipmentSlot;
import dev.tentaworks.registry.TentaworksBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import dev.tentaworks.network.RigMotionPacket;
import dev.tentaworks.rig.RigState;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.CreativeTabsScreenPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.tentaworks.rig.RigEnchants;
import dev.tentaworks.rig.TentacleRigItem;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dev-only client script: -Ptentaworks.script=file, run once a world is joined.
 * Lines: wait N | await name (run/signal/name exists) | signal name (writes run/signal/c_name for rcon.py #await) | cmd /x (needs op) | shot name (toasts cleared first) | hud on|off | close | quit
 * | hold up|down|left|right|jump|shift|sprint on|off (re-pressed every tick) | taps N (jump down 1 tick, up 2, N times)
 * | doublejump (taps 2) | look yaw pitch | trace on|off (TRACE line per tick) | mark tag (CPOS line + /tentaworks pos tag)
 * | freeze ms (blocks the client thread: a real hitch, no packets) | TITLE lines whenever the on-screen subtitle changes (Create's backtank warnings)
 * | bind key.tentaworks.toggle key.keyboard.r (rebinds a KeyMapping, not saved to options.txt) | scroll N delta (N-th ScrollInput of the open screen, delta steps)
 * | rigs tag (CRIG lines: open Rig Settings screen values + Rig Settings component per slot) | screen (SCREEN line: open screen class)
 * | rawsettings mainhand|offhand|chest height speed double_jump|key (sends the settings packet as is, unclamped)
 * | tooltips shift|noshift ns:id... (Create ItemDescription of each item, as with/without Shift)
 * | slot N [quick|pickup] (real container click on menu slot N) | enchbutton N (enchanting table option N, 0-based) | menu tag (CMENU lines: menu, anvil cost,
 * enchanting offers, non-inventory slots with enchantments) | reach tag (CREACH: effective Reach, top Speed, Thrift and Swift factors of the worn Rig)
 * | mouse x y (GUI coordinates, relative to an open container screen) | ponder ns:id (Ponder screen of that item) | ponderseek ticks
 * | goggles tag (CGOGGLE lines: the goggle text of the looked-at block entity)
 * | clearchat | jei ns:id recipes|uses ns:category (JEI recipe view, JEI only; our recipes only when the view has any) | itemtip chest|mainhand|N tag (CTIP lines: the real tooltip of that stack, drawn for a shot)
 * | icons ns:id... (items drawn large) | griplines on|off (debug Grip lines) | perf tag (CPERF: Tentacle draw time since the last perf) | sounds tag (CSOUND: Rig sounds heard, plants seen)
 * | gait on|off (GAIT line per tick: own gait slots) | key key.keyboard.e (one click; key.mouse.right = use) | tab ns:id (select a creative tab, any page) | backtanks (BACKTANK lines: cogs/shaft partial and default name per backtank block) | rejoin ticks (disconnect, reconnect to the same server after ticks) | view first|third|front.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class DevScript {
    private static final Logger LOG = LoggerFactory.getLogger("tentaworks-script");
    private static final Set<KeyMapping> HELD = new LinkedHashSet<>();
    private static final Deque<Boolean> JUMP_SEQ = new ArrayDeque<>();
    private static Deque<String> lines;
    private static int waitTicks;
    private static boolean trace;
    private static long clientTicks;
    private static Vec3 lastPos;
    private static int rejoinTicks;
    private static String rejoinAddress;
    private static boolean inWorld;
    private static String lastLogged;
    private static int outTicks;
    private static Field subtitleField;
    private static String lastSubtitle;
    private static boolean toastsCleared;
    private static String forge;
    private static final boolean SABLE = ModList.get() != null && ModList.get().isLoaded("sable");
    private static List<String> shipPoses;

    private DevScript() {
    }

    @SubscribeEvent
    static void onPreTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (KeyMapping k : HELD) k.setDown(true);
        if (!JUMP_SEQ.isEmpty()) mc.options.keyJump.setDown(JUMP_SEQ.poll());
    }

    /** Forged client (phase 8): "zero" sends a second motion packet per tick claiming no motion; the server keeps the latest. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onInputLast(MovementInputUpdateEvent event) {
        if (trace && SABLE && event.getEntity() instanceof LocalPlayer p) shipPoses = DevShipsClient.poses((ClientLevel) p.level());
        if (forge == null || !(event.getEntity() instanceof LocalPlayer) || RigClient.state() == RigState.STOWED) return;
        if (forge.equals("zero")) PacketDistributor.sendToServer(new RigMotionPacket(RigClient.state(), Vec3.ZERO, RigClient.gait().wire()));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        String file = System.getProperty("tentaworks.script");
        if (file == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (rejoinTicks > 0) {
            if (mc.level == null && --rejoinTicks == 0) {
                LOG.info("script rejoin {}", rejoinAddress);
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(rejoinAddress),
                        new ServerData("dev", rejoinAddress, ServerData.Type.OTHER), false, null);
            }
            return;
        }
        if (mc.level == null || mc.player == null) {
            inWorld = false;
            if (lines != null && ++outTicks > 400) {
                LOG.info("script: not in a world for 20 s, quitting");
                mc.stop();
            }
            return;
        }
        outTicks = 0;
        if (!inWorld) {
            inWorld = true;
            if (lines != null) waitTicks = 40;
        }
        clientTicks++;
        if (trace) trace(mc, mc.player);
        logTitle(mc);
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        if (lines == null) {
            try {
                lines = new ArrayDeque<>(Files.readAllLines(Path.of(file)));
            } catch (IOException e) {
                LOG.error("cannot read script {}", file, e);
                lines = new ArrayDeque<>();
            }
            waitTicks = 40;
            LOG.info("script start ({})", mc.getSingleplayerServer() != null ? "integrated" : "remote server");
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        while (!lines.isEmpty()) {
            String line = lines.poll().trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!line.equals(lastLogged)) LOG.info("script: {}", line);
            lastLogged = line;
            String[] a = line.split("\\s+");
            switch (a[0]) {
                case "await" -> {
                    Path signal = mc.gameDirectory.toPath().resolve("signal").resolve(a[1]);
                    if (!Files.exists(signal)) {
                        lines.addFirst(line);
                        waitTicks = 2;
                        return;
                    }
                }
                case "signal" -> {
                    try {
                        Path dir = mc.gameDirectory.toPath().resolve("signal");
                        Files.createDirectories(dir);
                        Files.writeString(dir.resolve("c_" + a[1]), "");
                    } catch (IOException e) {
                        LOG.error("cannot write signal {}", a[1], e);
                    }
                }
                case "wait" -> {
                    waitTicks = Integer.parseInt(a[1]);
                    return;
                }
                case "cmd" -> mc.player.connection.sendCommand(line.substring(4).trim().replaceFirst("^/", ""));
                case "shot" -> {
                    if (!toastsCleared) {
                        mc.getToasts().clear();
                        toastsCleared = true;
                        lines.addFirst(line);
                        waitTicks = 2;
                        return;
                    }
                    toastsCleared = false;
                    String name = a[1] + ".png";
                    Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> LOG.info("screenshot {}: {}", name, msg.getString()));
                    waitTicks = 2;
                    return;
                }
                case "hud" -> mc.options.hideGui = a[1].equals("off");
                case "tab" -> selectTab(mc, a[1]);
                case "backtanks" -> logBacktanks();
                case "close" -> mc.setScreen(null);
                case "onclose" -> {
                    if (mc.screen != null) mc.screen.onClose();
                }
                case "linksource" -> linkSource(mc, a[1]);
                case "clip" -> {
                    Vec3 from = mc.player.position().add(0, 0.1, 0), to = mc.player.position().subtract(0, 3, 0);
                    var ctx = new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player);
                    var hit = mc.level.clip(ctx);
                    var world = net.minecraft.world.level.BlockGetter.traverseBlocks(from, to, ctx, (c, pos) -> {
                        var st = mc.level.getBlockState(pos);
                        return mc.level.clipWithInteractionOverride(from, to, pos, c.getBlockShape(st, mc.level, pos), st);
                    }, c -> null);
                    LOG.info("CCLIP {} level.clip={} {} {} world={}", a[1], hit.getType(), hit.getBlockPos().toShortString(), fmt(hit.getLocation()), world == null ? "miss" : world.getBlockPos().toShortString() + " " + fmt(world.getLocation()));
                }
                case "bind" -> {
                    KeyMapping km = Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals(a[1])).findFirst().orElseThrow();
                    km.setKey(InputConstants.getKey(a[2]));
                    KeyMapping.resetMapping();
                    LOG.info("script: bound {} to {}", a[1], a[2]);
                }
                case "scroll" -> {
                    List<ScrollInput> inputs = mc.screen == null ? List.of() : mc.screen.children().stream()
                            .filter(c -> c instanceof ScrollInput).map(c -> (ScrollInput) c).toList();
                    int n = Integer.parseInt(a[1]), delta = Integer.parseInt(a[2]);
                    if (n >= inputs.size()) {
                        LOG.warn("script: no ScrollInput {} on {}", n, mc.screen);
                    } else {
                        ScrollInput in = inputs.get(n);
                        for (int i = 0; i < Math.abs(delta); i++)
                            in.mouseScrolled(in.getX() + in.getWidth() / 2.0, in.getY() + in.getHeight() / 2.0, 0, Math.signum(delta));
                        LOG.info("script: scrolled {} by {} -> {}", n, delta, in.getState());
                    }
                }
                case "screen" -> LOG.info("SCREEN {}", mc.screen == null ? "none" : mc.screen.getClass().getName());
                case "rigs" -> {
                    if (mc.screen instanceof RigSettingsScreen rs) LOG.info("CRIG {} screen settings={}", a[1], rs.settings());
                    for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.CHEST}) {
                        ItemStack st = mc.player.getItemBySlot(slot);
                        if (!st.isEmpty()) LOG.info("CRIG {} slot={} item={} settings={}", a[1], slot.getName(), st.getItemHolder().getRegisteredName(),
                                st.get(TentaworksDataComponents.RIG_SETTINGS.get()));
                    }
                }
                case "rawsettings" -> {
                    EquipmentSlot slot = EquipmentSlot.byName(a[1]);
                    RigSettings raw = new RigSettings(Float.parseFloat(a[2]), Float.parseFloat(a[3]),
                            a[4].equals("key") ? RigSettings.ActivationMode.KEY : RigSettings.ActivationMode.DOUBLE_JUMP);
                    PacketDistributor.sendToServer(new RigSettingsPacket(slot, raw));
                    LOG.info("script: raw settings {} {}", slot.getName(), raw);
                }
                case "tooltips" -> {
                    List<ItemStack> stacks = new ArrayList<>();
                    for (int i = 2; i < a.length; i++) stacks.add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(a[i]))));
                    mc.setScreen(new TooltipScreen(stacks, a[1].equals("shift")));
                }
                case "key" -> {
                    KeyMapping.click(InputConstants.getKey(a[1]));
                    waitTicks = 4;
                    return;
                }
                case "hold" -> {
                    KeyMapping k = switch (a[1]) {
                        case "up" -> mc.options.keyUp;
                        case "down" -> mc.options.keyDown;
                        case "left" -> mc.options.keyLeft;
                        case "right" -> mc.options.keyRight;
                        case "jump" -> mc.options.keyJump;
                        case "shift" -> mc.options.keyShift;
                        case "sprint" -> mc.options.keySprint;
                        default -> throw new IllegalArgumentException(a[1]);
                    };
                    if (a[2].equals("on")) {
                        HELD.add(k);
                    } else {
                        HELD.remove(k);
                        k.setDown(false);
                    }
                }
                case "holdkey" -> {
                    KeyMapping km = Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals(a[1])).findFirst().orElse(null);
                    if (km == null) {
                        LOG.warn("script: no key mapping {}", a[1]);
                    } else if (a[2].equals("on")) {
                        HELD.add(km);
                    } else {
                        HELD.remove(km);
                        km.setDown(false);
                    }
                }
                case "taps", "doublejump" -> {
                    int n = a[0].equals("doublejump") ? 2 : Integer.parseInt(a[1]);
                    for (int i = 0; i < n; i++) {
                        JUMP_SEQ.add(true);
                        JUMP_SEQ.add(false);
                        JUMP_SEQ.add(false);
                    }
                    waitTicks = JUMP_SEQ.size() + 1;
                    return;
                }
                case "look" -> {
                    float yaw = Float.parseFloat(a[1]), pitch = Float.parseFloat(a[2]);
                    mc.player.setYRot(yaw);
                    mc.player.setXRot(pitch);
                    mc.player.yRotO = yaw;
                    mc.player.xRotO = pitch;
                }
                case "trace" -> trace = a[1].equals("on");
                case "griplines" -> GripLineRenderer.on = a[1].equals("on");
                case "icons" -> {
                    List<ItemStack> stacks = new ArrayList<>();
                    for (int i = 1; i < a.length; i++) stacks.add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(a[i]))));
                    mc.setScreen(new IconScreen(stacks));
                    waitTicks = 3;
                    return;
                }
                case "perf" -> DevRigStats.perf(a[1]);
                case "input" -> DevRigStats.input(a[1]);
                case "forge" -> {
                    forge = a[1].equals("off") ? null : a[1];
                    LOG.info("script: forge {}", a[1]);
                }
                case "sounds" -> DevRigStats.sounds(a[1]);
                case "gait" -> DevRigStats.gaitTrace = a[1].equals("on");
                case "freeze" -> {
                    try {
                        Thread.sleep(Long.parseLong(a[1]));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                case "view" -> mc.options.setCameraType(switch (a[1]) {
                    case "third" -> CameraType.THIRD_PERSON_BACK;
                    case "front" -> CameraType.THIRD_PERSON_FRONT;
                    default -> CameraType.FIRST_PERSON;
                });
                case "mark" -> {
                    LocalPlayer p = mc.player;
                    LOG.info("CPOS {} gt={} pos={} state={} fly={}", a[1], mc.level.getGameTime(), fmt(p.position()), RigClient.state(), p.getAbilities().flying);
                    mc.player.connection.sendCommand("tentaworks pos " + a[1]);
                }
                case "rejoin" -> {
                    rejoinAddress = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "localhost:25609";
                    rejoinTicks = Integer.parseInt(a[1]);
                    HELD.clear();
                    mc.level.disconnect();
                    mc.disconnect();
                    return;
                }
                case "slot" -> {
                    AbstractContainerMenu m = mc.player.containerMenu;
                    int n = Integer.parseInt(a[1]);
                    ClickType type = a.length > 2 && a[2].equals("pickup") ? ClickType.PICKUP : ClickType.QUICK_MOVE;
                    mc.gameMode.handleInventoryMouseClick(m.containerId, n, 0, type, mc.player);
                    LOG.info("script: clicked slot {} ({}) in {}", n, type, m.getClass().getSimpleName());
                    waitTicks = 3;
                    return;
                }
                case "enchbutton" -> {
                    AbstractContainerMenu m = mc.player.containerMenu;
                    mc.gameMode.handleInventoryButtonClick(m.containerId, Integer.parseInt(a[1]));
                    LOG.info("script: enchant button {}", a[1]);
                    waitTicks = 3;
                    return;
                }
                case "menu" -> logMenu(mc, a[1]);
                case "clearchat" -> mc.gui.getChat().clearMessages(false);
                case "ponder" -> {
                    net.createmod.ponder.foundation.ui.PonderUI ui = net.createmod.ponder.foundation.ui.PonderUI.of(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(a[1]))));
                    net.createmod.catnip.gui.ScreenOpener.open(ui);
                    LOG.info("script ponder {} scene={}", a[1], ui.getActiveScene().getId());
                    waitTicks = 10;
                    return;
                }
                case "ponderseek" -> {
                    if (mc.screen instanceof net.createmod.ponder.foundation.ui.PonderUI ui) {
                        ui.seekToTime(Integer.parseInt(a[1]));
                        LOG.info("script ponder seek {} scene={} total={}", a[1], ui.getActiveScene().getId(), ui.getActiveScene().getTotalTime());
                    } else LOG.warn("script ponderseek: no Ponder screen ({})", mc.screen);
                    waitTicks = 4;
                    return;
                }
                case "goggles" -> {
                    if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                            && mc.level.getBlockEntity(hit.getBlockPos()) instanceof com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation g) {
                        List<Component> out = new ArrayList<>();
                        g.addToGoggleTooltip(out, false);
                        for (Component c : out) LOG.info("CGOGGLE {} {}", a[1], c.getString());
                    } else LOG.info("CGOGGLE {} none {}", a[1], mc.hitResult == null ? "-" : mc.hitResult.getLocation());
                }
                case "jei" -> {
                    if (net.neoforged.fml.ModList.get().isLoaded("jei"))
                        DevJei.open(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(a[1]))), a[2], a[3]);
                    else LOG.info("JEI not loaded");
                    waitTicks = 10;
                    return;
                }
                case "itemtip" -> {
                    ItemStack st = switch (a[1]) {
                        case "chest" -> mc.player.getItemBySlot(EquipmentSlot.CHEST);
                        case "mainhand" -> mc.player.getMainHandItem();
                        default -> mc.player.getInventory().getItem(Integer.parseInt(a[1]));
                    };
                    List<Component> tip = Screen.getTooltipFromItem(mc, st);
                    for (Component c : tip) LOG.info("CTIP {} {}", a[2], c.getString());
                    mc.setScreen(new ItemTipScreen(tip));
                    waitTicks = 3;
                    return;
                }
                case "reach" -> {
                    ItemStack worn = mc.player.getItemBySlot(EquipmentSlot.CHEST);
                    LOG.info("CREACH {} reach={} top={} thrift={} swift={}", a[1], String.format("%.2f", TentacleRigItem.reach(mc.player)),
                            String.format("%.2f", worn.getItem() instanceof TentacleRigItem ? TentacleRigItem.topSpeed(worn) : 0),
                            String.format("%.3f", RigEnchants.thrift(worn)), String.format("%.3f", RigEnchants.swift(worn)));
                }
                case "mouse" -> {
                    double gx = Double.parseDouble(a[1]), gy = Double.parseDouble(a[2]);
                    if (mc.screen instanceof AbstractContainerScreen<?> cs) {
                        gx += cs.getGuiLeft();
                        gy += cs.getGuiTop();
                    }
                    double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
                    GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), gx * scale, gy * scale);
                    try {
                        for (String f : new String[]{"xpos", "ypos"}) {
                            Field field = MouseHandler.class.getDeclaredField(f);
                            field.setAccessible(true);
                            field.setDouble(mc.mouseHandler, (f.equals("xpos") ? gx : gy) * scale);
                        }
                    } catch (ReflectiveOperationException e) {
                        LOG.error("script: mouse", e);
                    }
                    waitTicks = 2;
                    return;
                }
                case "quit" -> {
                    mc.stop();
                    return;
                }
                default -> LOG.warn("script: unknown line {}", line);
            }
        }
    }

    /** Items drawn large in a row (1x slot size in the top row for reference, then 6x). */
    static final class IconScreen extends Screen {
        private final List<ItemStack> stacks;

        IconScreen(List<ItemStack> stacks) {
            super(Component.literal("icons"));
            this.stacks = stacks;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, width, height, 0xFF8B8B8B);
            for (int i = 0; i < stacks.size(); i++) {
                g.renderItem(stacks.get(i), 8 + i * 20, 8);
                g.pose().pushPose();
                g.pose().translate(8 + i * 100, 40, 0);
                g.pose().scale(6, 6, 1);
                g.renderItem(stacks.get(i), 0, 0);
                g.pose().popPose();
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    static final class ItemTipScreen extends Screen {
        private final List<Component> lines;

        ItemTipScreen(List<Component> lines) {
            super(Component.literal("itemtip"));
            this.lines = lines;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.render(g, mouseX, mouseY, partialTick);
            g.renderComponentTooltip(font, lines, 8, 24);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    static final class TooltipScreen extends Screen {
        private final List<ItemStack> stacks;
        private final boolean shift;

        TooltipScreen(List<ItemStack> stacks, boolean shift) {
            super(Component.literal("tooltips"));
            this.stacks = stacks;
            this.shift = shift;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.render(g, mouseX, mouseY, partialTick);
            int colW = width / Math.max(1, stacks.size());
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack s = stacks.get(i);
                Item item = s.getItem();
                List<Component> lines = new ArrayList<>();
                lines.add(s.getHoverName());
                ItemDescription d = ItemDescription.create(item, FontHelper.Palette.STANDARD_CREATE);
                lines.addAll(shift ? d.linesOnShift() : d.lines());
                g.renderComponentTooltip(font, lines, 4 + i * colW, 24);
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private static void logMenu(Minecraft mc, String tag) {
        AbstractContainerMenu m = mc.player.containerMenu;
        LOG.info("CMENU {} class={} id={}", tag, m.getClass().getSimpleName(), m.containerId);
        if (m instanceof AnvilMenu anvil) LOG.info("CMENU {} anvil cost={}", tag, anvil.getCost());
        if (m instanceof EnchantmentMenu e) {
            IdMap<Holder<Enchantment>> ids = mc.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
            for (int i = 0; i < 3; i++) {
                Holder<Enchantment> h = e.enchantClue[i] < 0 ? null : ids.byId(e.enchantClue[i]);
                LOG.info("CMENU {} offer={} cost={} clue={}:{}", tag, i + 1, e.costs[i], h == null ? "none" : h.getRegisteredName(), e.levelClue[i]);
            }
        }
        for (Slot slot : m.slots) {
            if (slot.container == mc.player.getInventory() || !slot.hasItem()) continue;
            ItemStack st = slot.getItem();
            List<String> ench = new ArrayList<>();
            for (Object2IntMap.Entry<Holder<Enchantment>> x : st.getTagEnchantments().entrySet()) ench.add(x.getKey().getRegisteredName() + ":" + x.getIntValue());
            for (Object2IntMap.Entry<Holder<Enchantment>> x : st.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet())
                ench.add("book " + x.getKey().getRegisteredName() + ":" + x.getIntValue());
            ench.sort(null);
            LOG.info("CMENU {} slot={} item={} count={} ench={}", tag, slot.index, st.getItemHolder().getRegisteredName(), st.getCount(), ench);
        }
    }

    private static void logBacktanks() {
        for (Block block : new Block[]{AllBlocks.COPPER_BACKTANK.get(), AllBlocks.NETHERITE_BACKTANK.get(),
                TentaworksBlocks.COPPER_TENTACLE_RIG.get(), TentaworksBlocks.NETHERITE_TENTACLE_RIG.get()}) {
            BlockState state = block.defaultBlockState();
            String cogs = BacktankRenderer.getCogsModel(state) == AllPartialModels.NETHERITE_BACKTANK_COGS ? "netherite" : "copper";
            String shaft = BacktankRenderer.getShaftModel(state) == AllPartialModels.NETHERITE_BACKTANK_SHAFT ? "netherite" : "copper";
            LOG.info("BACKTANK {} cogs={} shaft={} name=\"{}\"", block.builtInRegistryHolder().key().location(), cogs, shaft,
                    BacktankBlockEntity.getDefaultName(state).getString());
        }
    }

    @SuppressWarnings("unchecked")
    private static void selectTab(Minecraft mc, String id) {
        if (!(mc.screen instanceof CreativeModeInventoryScreen screen)) {
            LOG.warn("script: tab needs the creative inventory open");
            return;
        }
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.parse(id));
        try {
            Field pages = CreativeModeInventoryScreen.class.getDeclaredField("pages");
            pages.setAccessible(true);
            for (CreativeTabsScreenPage page : (List<CreativeTabsScreenPage>) pages.get(screen)) {
                if (page.getVisibleTabs().contains(tab)) screen.setCurrentPage(page);
            }
            Method select = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            select.setAccessible(true);
            select.invoke(screen, tab);
            LOG.info("script: tab {} selected", id);
        } catch (ReflectiveOperationException e) {
            LOG.error("script: cannot select tab {}", id, e);
        }
    }

    private static void logTitle(Minecraft mc) {
        try {
            if (subtitleField == null) {
                subtitleField = Gui.class.getDeclaredField("subtitle");
                subtitleField.setAccessible(true);
            }
            Component sub = (Component) subtitleField.get(mc.gui);
            String text = sub == null ? null : sub.getString();
            if (text != null && !text.equals(lastSubtitle)) LOG.info("TITLE gt={} subtitle=\"{}\"", mc.level.getGameTime(), text);
            lastSubtitle = text;
        } catch (ReflectiveOperationException e) {
            LOG.error("script: cannot read the subtitle", e);
            subtitleField = null;
        }
    }

    private static void trace(Minecraft mc, LocalPlayer p) {
        Vec3 pos = p.position();
        Vec3 d = lastPos == null ? Vec3.ZERO : pos.subtract(lastPos);
        lastPos = pos;
        LOG.info("TRACE t={} gt={} pos={} d={} cmd={} held={} state={} fly={} mayfly={} press={} jumpkey={} ground={} hp={} air={} ff={} sheld={} carry={}",
                clientTicks, mc.level.getGameTime(), fmt(pos), fmt(d), fmt(RigClient.motion()), RigClient.heldCount(), RigClient.state(),
                p.getAbilities().flying, p.mayFly(), RigClient.jumpPressed(), mc.options.keyJump.isDown(), p.onGround(), p.getHealth(), BacktankUtil.getAir(p.getItemBySlot(EquipmentSlot.CHEST)), p.isFallFlying(),
                RigClient.shipGrips(), RigClient.carryShip() == null ? "-" : RigClient.carryShip().id());
        if (!SABLE) return;
        List<String> poses = shipPoses != null ? shipPoses : DevShipsClient.poses(mc.level);
        shipPoses = null;
        for (String pose : poses) LOG.info("CSHIP gt={} {}", mc.level.getGameTime(), pose);
    }

    /** Picks a source in the open Display Link screen through its own selector (reflection on the screen's fields). */
    @SuppressWarnings("unchecked")
    private static void linkSource(Minecraft mc, String id) {
        try {
            Object screen = mc.screen;
            Field f = screen.getClass().getDeclaredField("sources");
            f.setAccessible(true);
            List<com.simibubi.create.api.behaviour.display.DisplaySource> sources = (List<com.simibubi.create.api.behaviour.display.DisplaySource>) f.get(screen);
            List<String> ids = sources.stream().map(x -> String.valueOf(com.simibubi.create.api.registry.CreateBuiltInRegistries.DISPLAY_SOURCE.getKey(x))).toList();
            Field sel = screen.getClass().getDeclaredField("sourceTypeSelector");
            sel.setAccessible(true);
            ScrollInput in = (ScrollInput) sel.get(screen);
            int i = ids.indexOf(id);
            if (in != null && i >= 0) {
                in.setState(i);
                in.onChanged();
            }
            LOG.info("script: link sources {} selector={} picked {} -> {}", ids, in != null, id, in == null ? 0 : in.getState());
        } catch (ReflectiveOperationException | ClassCastException | NullPointerException e) {
            LOG.error("script: linksource", e);
        }
    }

    private static String fmt(Vec3 v) {
        return String.format("%.3f,%.3f,%.3f", v.x, v.y, v.z);
    }
}
