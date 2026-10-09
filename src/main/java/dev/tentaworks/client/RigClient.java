package dev.tentaworks.client;

import com.simibubi.create.AllSoundEvents;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.network.RigMotionPacket;
import dev.tentaworks.network.RigStatePacket;
import dev.tentaworks.rig.Grip;
import dev.tentaworks.rig.Grips;
import dev.tentaworks.rig.RigAir;
import dev.tentaworks.rig.RigEnchants;
import dev.tentaworks.rig.RigServer;
import dev.tentaworks.rig.RigSettings;
import dev.tentaworks.rig.RigState;
import dev.tentaworks.rig.TentacleGait;
import dev.tentaworks.rig.Tentacles;
import dev.tentaworks.rig.TentacleRigItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Wearer side of the Rig (adr/0001): reads the movement keys, moves the body along the Heading as far as it stays Held,
 * and tells the server. Vanilla jump, sneak and walk input are swallowed while the Rig carries.
 * Air and the Rig Settings come from the synced Rig stack; the server has the last word and corrects the state with a RigStatePacket.
 * The on/off input is the Double Jump, or the toggle key when the Rig's Activation Mode is key only.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class RigClient {
    public static final int DOUBLE_JUMP_TICKS = 7;

    private static RigState state = RigState.STOWED;
    private static final TentacleGait GAIT = new TentacleGait();
    private static int heldCount;
    private static Vec3 motion = Vec3.ZERO;
    private static boolean jumpWasDown;
    private static boolean jumpPressed;
    private static int ticks;
    private static int lastPress = -100;
    private static int activatedAt = -100;
    private static double liftTo = Double.NaN;
    private static boolean noRegrab;
    private static int warnedAt = -100;

    private RigClient() {
    }

    public static RigState state() {
        return state;
    }

    public static Grip[] grips() {
        return GAIT.grips();
    }

    public static TentacleGait gait() {
        return GAIT;
    }

    public static int heldCount() {
        return heldCount;
    }

    public static Vec3 motion() {
        return motion;
    }

    public static boolean jumpPressed() {
        return jumpPressed;
    }

    @SubscribeEvent
    static void onInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        Input input = event.getInput();
        ticks++;
        jumpPressed = input.jumping && !jumpWasDown;
        jumpWasDown = input.jumping;
        if (!TentacleRigItem.isWorn(player)) {
            while (TentaworksKeys.TOGGLE.consumeClick()) {
            }
            if (state != RigState.STOWED) setState(player, RigState.STOWED);
            return;
        }
        RigSettings settings = RigSettings.of(RigAir.worn(player));
        if (settings.activation() == RigSettings.ActivationMode.KEY) {
            while (TentaworksKeys.TOGGLE.consumeClick()) toggle(player, settings);
        } else {
            while (TentaworksKeys.TOGGLE.consumeClick()) {
            }
            if (jumpPressed && onJumpPress(player, settings)) input.jumping = false;
        }
        if (state == RigState.STOWED) return;

        boolean up = input.jumping, down = input.shiftKeyDown;
        int forward = (input.up ? 1 : 0) - (input.down ? 1 : 0);
        int left = (input.left ? 1 : 0) - (input.right ? 1 : 0);
        input.jumping = false;
        input.shiftKeyDown = false;
        if (state != RigState.FALLING) {
            input.up = input.down = input.left = input.right = false;
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
        }

        double reach = TentacleRigItem.reach(player);
        double step = settings.speed() * RigEnchants.swift(RigAir.worn(player)) / 20.0;
        Vec3 body = Grips.body(player);
        List<Grips.Face> faces = Grips.faces(player.level(), body, reach + Math.max(1.0, step + 0.5));
        heldCount = Grips.count(faces, body, reach);
        boolean held = heldCount >= Grips.HELD;
        boolean air = RigAir.has(player);
        boolean worn = TentacleRigItem.wornOut(RigAir.worn(player));
        if (state.drains() && !air) state = RigState.CRAWL;
        else if (state == RigState.CRAWL && RigAir.free(player)) state = RigState.ACTIVE;
        if (state.carries() && !held) state = RigState.FALLING;
        else if (state == RigState.FALLING && held && air && !worn) state = RigState.ACTIVE;
        else if (state == RigState.FALLING && held && !noRegrab && !worn) state = RigState.CRAWL;
        else if (state == RigState.FALLING && player.onGround()) {
            setState(player, RigState.STOWED);
            return;
        }
        if (worn && state == RigState.ACTIVE) {
            state = RigState.SETTING_DOWN;
            wornWarning(player);
        }
        if ((state == RigState.SETTING_DOWN || state == RigState.CRAWL) && player.onGround()) {
            setState(player, RigState.STOWED);
            return;
        }

        if (state.carries() && player.isFallFlying()) player.stopFallFlying();
        switch (state) {
            case ACTIVE -> {
                Vec3 heading = heading(player, forward, left, up, down);
                if (!Double.isNaN(liftTo)) {
                    double dy = liftTo - player.getY();
                    if (up || down || Math.abs(dy) < 0.01) liftTo = Double.NaN;
                    else heading = heading.add(0, Math.signum(dy), 0);
                }
                Vec3 wish = heading.lengthSqr() < 1.0E-6 ? Vec3.ZERO : heading.normalize().scale(step);
                if (!Double.isNaN(liftTo) && Math.abs(wish.y) > Math.abs(liftTo - player.getY()))
                    wish = new Vec3(wish.x, liftTo - player.getY(), wish.z);
                motion = Grips.clampHeld(player.level(), player, wish, reach, faces);
                if (!Double.isNaN(liftTo) && Math.abs(motion.y) < 1.0E-4) liftTo = Double.NaN;
                player.setDeltaMovement(motion);
                player.resetFallDistance();
            }
            case SETTING_DOWN -> {
                motion = Grips.clampHeld(player.level(), player, new Vec3(0, -step, 0), reach, faces);
                if (motion.y > -1.0E-4) {
                    setState(player, RigState.STOWED);
                    return;
                }
                player.setDeltaMovement(motion);
                player.resetFallDistance();
            }
            case CRAWL -> {
                motion = Grips.clampHeld(player.level(), player, new Vec3(0, -RigAir.crawlStep(), 0), reach, faces);
                if (worn && motion.y > -1.0E-4) {
                    setState(player, RigState.STOWED);
                    return;
                }
                player.setDeltaMovement(motion);
                player.resetFallDistance();
            }
            case FALLING -> {
                motion = player.getDeltaMovement();
                Vec3 catchAt = noRegrab || worn ? null : Grips.firstHeld(player.level(), player, motion, reach);
                if (catchAt != null) {
                    motion = catchAt;
                    player.setDeltaMovement(motion);
                }
            }
            default -> motion = player.getDeltaMovement();
        }
        double overshoot = TentacleGait.overshoot(TentacleRigItem.topSpeed(RigAir.worn(player)) * 1.05 / 20.0);
        Vec3 moved = Grips.collide(player.level(), player, motion);
        GAIT.tick(player.level(), faces, body.add(moved), player.yBodyRot, moved, reach, overshoot);
        noFlying(player);
        Tentacles wire = GAIT.wire();
        RigVisuals.local(player, state, wire);
        PacketDistributor.sendToServer(new RigMotionPacket(state, motion, wire));
    }

    private static boolean onJumpPress(LocalPlayer player, RigSettings settings) {
        boolean quick = ticks - lastPress <= DOUBLE_JUMP_TICKS;
        if (state == RigState.STOWED) {
            if (quick && TentacleRigItem.wornOut(RigAir.worn(player))) {
                wornWarning(player);
                lastPress = -100;
                return false;
            }
            if (!quick || !RigAir.has(player)) {
                lastPress = ticks;
                return false;
            }
            lastPress = -100;
            return toggle(player, settings);
        }
        if (player.getAbilities().mayfly && ticks - activatedAt <= DOUBLE_JUMP_TICKS) {
            setState(player, RigState.STOWED);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            return true;
        }
        if (quick && (state == RigState.ACTIVE || state == RigState.CRAWL)) {
            lastPress = -100;
            return toggle(player, settings);
        }
        lastPress = ticks;
        return false;
    }

    /**
     * The Rig's on/off (a Double Jump, or the toggle key): activates a Stowed Rig that has Air (mid-air it catches at
     * the first Grip), starts Setting Down when Active, and lets go in Crawl (Stowed, the wearer falls).
     */
    private static boolean toggle(LocalPlayer player, RigSettings settings) {
        switch (state) {
            case STOWED -> {
                if (TentacleRigItem.wornOut(RigAir.worn(player))) {
                    wornWarning(player);
                    return false;
                }
                if (!RigAir.has(player)) return false;
                activatedAt = ticks;
                double h = settings.standingHeight();
                liftTo = floorBelow(player, h) + h;
                setState(player, RigState.ACTIVE);
                return true;
            }
            case ACTIVE -> {
                state = RigState.SETTING_DOWN;
                return true;
            }
            case CRAWL -> {
                setState(player, RigState.STOWED);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Create's backtank warning style (subtitle under an empty title): a worn-out Rig refuses to carry. */
    private static void wornWarning(LocalPlayer player) {
        if (ticks - warnedAt < 40) return;
        warnedAt = ticks;
        Gui gui = Minecraft.getInstance().gui;
        gui.setTimes(10, 40, 10);
        gui.setSubtitle(Component.literal("⚠ ").withStyle(ChatFormatting.RED)
                .append(Component.translatable("tentaworks.rig.worn_out.warning").withStyle(ChatFormatting.GRAY)));
        gui.setTitle(CommonComponents.EMPTY);
        player.playSound(AllSoundEvents.DENY.getMainEvent(), 1.0F, 1.25F);
    }

    private static Vec3 heading(LocalPlayer player, int forward, int left, boolean up, boolean down) {
        Vec3 look = player.getViewVector(1.0F);
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 leftDir = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        return look.scale(forward).add(leftDir.scale(left)).add(0, (up ? 1 : 0) - (down ? 1 : 0), 0);
    }

    /**
     * World floor under the feet within Standing Height + 1.5. Traces world blocks only: with Sable loaded, {@code Level.clip}
     * also hits Ships and reports those hits in plot coordinates, and Ship blocks are not Grips.
     */
    private static double floorBelow(LocalPlayer player, double standingHeight) {
        Level level = player.level();
        Vec3 from = player.position().add(0, 0.1, 0);
        Vec3 to = player.position().subtract(0, standingHeight + 1.5, 0);
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        BlockHitResult hit = BlockGetter.traverseBlocks(from, to, ctx, (c, pos) -> {
            BlockState s = level.getBlockState(pos);
            return level.clipWithInteractionOverride(from, to, pos, c.getBlockShape(s, level, pos), s);
        }, c -> null);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return Double.NaN;
        double y = hit.getLocation().y;
        return y <= from.y + 1.0E-6 && y >= to.y - 1.0E-6 ? y : Double.NaN;
    }

    private static void setState(LocalPlayer player, RigState next) {
        state = next;
        if (next == RigState.STOWED) {
            GAIT.clear();
            motion = Vec3.ZERO;
            liftTo = Double.NaN;
            heldCount = 0;
            noRegrab = false;
            noFlying(player);
            RigVisuals.local(player, next, Tentacles.NONE);
            PacketDistributor.sendToServer(new RigMotionPacket(next, Vec3.ZERO, Tentacles.NONE));
        }
    }

    private static void noFlying(LocalPlayer player) {
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    private static void reset() {
        state = RigState.STOWED;
        GAIT.clear();
        motion = Vec3.ZERO;
        liftTo = Double.NaN;
        heldCount = 0;
        lastPress = -100;
        noRegrab = false;
    }

    public static void onState(RigStatePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && packet.entityId() == mc.player.getId()) {
            if (packet.state() == RigState.STOWED) {
                reset();
                RigVisuals.local(mc.player, RigState.STOWED, Tentacles.NONE);
            } else {
                if (packet.state() == RigState.FALLING && state == RigState.CRAWL) noRegrab = true;
                state = packet.state();
            }
            return;
        }
        RigVisuals.remote(packet);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (TentaworksKeys.SETTINGS.consumeClick()) {
            if (mc.player != null && mc.screen == null && TentacleRigItem.isWorn(mc.player)) RigSettingsScreen.open(EquipmentSlot.CHEST);
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        RigVisuals.clear();
    }

    @SubscribeEvent
    static void onClone(ClientPlayerNetworkEvent.Clone event) {
        reset();
        RigVisuals.clear();
        AttributeInstance flight = event.getNewPlayer().getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight != null) flight.removeModifier(RigServer.FLOAT_ID);
    }
}
