package dev.tentaworks.client;

import com.simibubi.create.AllSoundEvents;
import dev.tentaworks.TentaworksIds;
import dev.tentaworks.compat.Frame;
import dev.tentaworks.compat.ShipFrames;
import dev.tentaworks.config.TentaworksClientConfig;
import dev.tentaworks.network.RigMotionPacket;
import dev.tentaworks.network.RigStatePacket;
import dev.tentaworks.network.TentaworksNetwork;
import dev.tentaworks.rig.Grip;
import dev.tentaworks.rig.Grips;
import dev.tentaworks.rig.RigAir;
import dev.tentaworks.rig.RigEnchants;
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
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Wearer side of the Rig (adr/0001): reads the movement keys, moves the body along the Heading as far as it stays Held,
 * and tells the server. Vanilla jump, sneak and walk input are swallowed while the Rig carries. Shift still counts as sneaking
 * for use and interaction ({@link #sneaking()}), without the crouch.
 * Air and the Rig Settings come from the synced Rig stack; the server has the last word and corrects the state with a RigStatePacket.
 * The on/off input is the Double Jump, or the toggle key when the Rig's Activation Mode is key only.
 * Riding a Ship: while carrying, the carry Ship (the one holding most current Grips, at least two, as on the server) moves
 * the wearer by its own move this tick on top of the Rig's motion, unless Sable already does (feet on its deck). Held is tested
 * where the body really ends up. The motion sent to the server and fed to the gait is the Rig's own, never the carry.
 * A Standing Height lift over a Ship deck aims at that deck where it is now, not where it was at activation.
 * Rides (adr/0003, {@link Rides}): starting one Stows the Rig, nothing Activates it during one, and the keys go to the Ride
 * untouched. During a Control Hold the Rig keeps its Grips and holds still; Shift and the walk keys stay on the input for the
 * control (no walk impulse), jump stays the Rig's.
 */
@EventBusSubscriber(modid = TentaworksIds.MOD_ID, value = Dist.CLIENT)
public final class RigClient {
    public static final int DOUBLE_JUMP_TICKS = 7;

    private static RigState state = RigState.STOWED;
    private static final TentacleGait GAIT = new TentacleGait();
    private static int heldCount;
    private static Frame carryShip;
    private static int shipGrips;
    private static Vec3 motion = Vec3.ZERO;
    private static boolean jumpWasDown;
    private static boolean jumpPressed;
    private static boolean sneak;
    private static int ticks;
    private static int lastPress = -100;
    private static int activatedAt = -100;
    private static double liftTo = Double.NaN;
    private static BlockPos liftBlock;
    private static Vec3 liftLocal;
    private static double liftHeight;
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

    /** The Rig's own motion this tick (without the Ship carry). */
    public static Vec3 motion() {
        return motion;
    }

    /** The Ship the current Grips ride with (null: the world). */
    public static Frame carryShip() {
        return carryShip;
    }

    /** How many of the current Grips are on a Ship. */
    public static int shipGrips() {
        return shipGrips;
    }

    public static boolean jumpPressed() {
        return jumpPressed;
    }

    /** Shift held while the Rig is out: the wearer sneaks (sneak + use works) but neither crouches nor moves like a sneak. */
    public static boolean sneaking() {
        return sneak && state != RigState.STOWED;
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
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
        if (Rides.riding(player)) {
            while (TentaworksKeys.TOGGLE.consumeClick()) {
            }
            lastPress = -100;
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

        boolean control = state.carries() && Rides.controlHold();
        boolean up = input.jumping && !control, down = input.shiftKeyDown && !control;
        sneak = down;
        int forward = control ? 0 : (input.up ? 1 : 0) - (input.down ? 1 : 0);
        int left = control ? 0 : (input.left ? 1 : 0) - (input.right ? 1 : 0);
        input.jumping = false;
        if (!control) input.shiftKeyDown = false;
        if (state != RigState.FALLING) {
            if (!control) input.up = input.down = input.left = input.right = false;
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
        }
        if (control) liftTo = Double.NaN;

        double reach = TentacleRigItem.reach(player);
        double step = settings.speed() * RigEnchants.swift(RigAir.worn(player)) / 20.0;
        Level level = player.level();
        ShipFrames ships = ShipFrames.get();
        Vec3 body = Grips.body(player);
        carryShip = carryShip(level, GAIT.grips());
        Vec3 shipMove = carryShip == null ? Vec3.ZERO : carryShip.carry(player.position());
        boolean tracked = carryShip != null && ships.tracking(player, carryShip);
        List<Grips.Face> faces = Grips.faces(level, body, reach + Math.max(1.0, step + 0.5) + shipMove.length());
        heldCount = Grips.count(faces, state.carries() ? body.add(shipMove) : body, reach);
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
        Vec3 shift = state.carries() ? shipMove : Vec3.ZERO;
        Vec3 carry = tracked ? Vec3.ZERO : shift;
        if (state.carries() && !tracked) ships.dropInherited(player);
        switch (state) {
            case ACTIVE -> {
                Vec3 heading = heading(player, forward, left, up, down);
                double feet = player.getY() + shift.y;
                if (!Double.isNaN(liftTo)) {
                    liftTo = liftTarget(level);
                    double dy = liftTo - feet;
                    if (up || down || Double.isNaN(dy) || Math.abs(dy) < 0.01) liftTo = Double.NaN;
                    else heading = heading.add(0, Math.signum(dy), 0);
                }
                Vec3 wish = heading.lengthSqr() < 1.0E-6 ? Vec3.ZERO : heading.normalize().scale(step);
                if (!Double.isNaN(liftTo) && Math.abs(wish.y) > Math.abs(liftTo - feet))
                    wish = new Vec3(wish.x, liftTo - feet, wish.z);
                motion = Grips.clampHeld(level, player, wish, reach, faces, shift);
                if (!Double.isNaN(liftTo) && Math.abs(motion.y) < 1.0E-4) liftTo = Double.NaN;
                player.setDeltaMovement(motion.add(carry));
                player.resetFallDistance();
            }
            case SETTING_DOWN -> {
                motion = Grips.clampHeld(level, player, new Vec3(0, -step, 0), reach, faces, shift);
                if (motion.y > -1.0E-4) {
                    setState(player, RigState.STOWED);
                    return;
                }
                player.setDeltaMovement(motion.add(carry));
                player.resetFallDistance();
            }
            case CRAWL -> {
                motion = Grips.clampHeld(level, player, new Vec3(0, -RigAir.crawlStep(), 0), reach, faces, shift);
                if (worn && motion.y > -1.0E-4) {
                    setState(player, RigState.STOWED);
                    return;
                }
                player.setDeltaMovement(motion.add(carry));
                player.resetFallDistance();
            }
            case FALLING -> {
                motion = player.getDeltaMovement();
                Vec3 catchAt = noRegrab || worn ? null : Grips.firstHeld(level, player, motion, reach);
                if (catchAt != null) {
                    motion = catchAt;
                    player.setDeltaMovement(motion);
                    ships.dropInherited(player);
                }
            }
            default -> motion = player.getDeltaMovement();
        }
        double overshoot = TentacleGait.overshoot(TentacleRigItem.topSpeed(RigAir.worn(player)) * 1.05 / 20.0);
        Vec3 moved = Grips.collide(level, player, motion);
        Floor floor = floorBelow(player, 0.35 * reach);
        GAIT.tick(level, faces, body.add(shift).add(moved), player.yBodyRot, moved, reach, overshoot, floor == null ? Double.NaN : floor.y());
        noFlying(player);
        Tentacles wire = GAIT.wire();
        RigVisuals.local(player, state, wire);
        TentaworksNetwork.sendToServer(new RigMotionPacket(state, motion, wire));
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
                ShipFrames.get().dropInherited(player);
                double h = settings.standingHeight();
                Floor floor = floorBelow(player, h);
                liftTo = floor == null ? Double.NaN : floor.y() + h;
                liftBlock = floor == null ? null : floor.plot();
                liftLocal = floor == null ? null : floor.local();
                liftHeight = h;
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

    /** The Heading: forward along the camera (client config {@code movement.forward = look}) or level only ({@code flat}). */
    private static Vec3 heading(LocalPlayer player, int forward, int left, boolean up, boolean down) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 look = TentaworksClientConfig.forward() == TentaworksClientConfig.ForwardMode.FLAT
                ? new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)) : player.getViewVector(1.0F);
        Vec3 leftDir = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        return look.scale(forward).add(leftDir.scale(left)).add(0, (up ? 1 : 0) - (down ? 1 : 0), 0);
    }

    /** A floor hit: world Y, and for a Ship deck its plot block and the hit point in plot coords (else null). */
    private record Floor(double y, BlockPos plot, Vec3 local) {
    }

    /** The lift target now: fixed for a world floor, the deck's current height + Standing Height for a Ship (NaN once it is gone). */
    private static double liftTarget(Level level) {
        if (liftBlock == null) return liftTo;
        ShipFrames ships = ShipFrames.get();
        Frame frame = ships.isPlot(level, liftBlock) ? ships.containing(level, liftBlock) : null;
        return frame == null ? Double.NaN : frame.toWorld(liftLocal).y + liftHeight;
    }

    /**
     * Floor under the feet within Standing Height + 1.5: a world block or a Ship deck. {@code Level.clip} also hits Ships
     * (with Sable) and reports those hits in plot coordinates, so a Ship hit is moved into the world by its pose; a plot hit
     * with no Ship to resolve it (Ships off in the config) traces world blocks only. Null when there is none.
     */
    private static Floor floorBelow(LocalPlayer player, double standingHeight) {
        Level level = player.level();
        Vec3 from = player.position().add(0, 0.1, 0);
        Vec3 to = player.position().subtract(0, standingHeight + 1.5, 0);
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        BlockHitResult hit = level.clip(ctx);
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        Vec3 at = hit.getLocation();
        BlockPos plot = null;
        Vec3 local = null;
        ShipFrames ships = ShipFrames.get();
        if (ships.isPlot(level, hit.getBlockPos())) {
            Frame frame = ships.containing(level, hit.getBlockPos());
            if (frame != null) {
                plot = hit.getBlockPos();
                local = at;
                at = frame.toWorld(at);
            } else {
                hit = worldClip(level, from, to, ctx);
                if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
                at = hit.getLocation();
            }
        }
        double y = at.y;
        return y <= from.y + 1.0E-6 && y >= to.y - 1.0E-6 ? new Floor(y, plot, local) : null;
    }

    private static BlockHitResult worldClip(Level level, Vec3 from, Vec3 to, ClipContext ctx) {
        return BlockGetter.traverseBlocks(from, to, ctx, (c, pos) -> {
            BlockState s = level.getBlockState(pos);
            return level.clipWithInteractionOverride(from, to, pos, c.getBlockShape(s, level, pos), s);
        }, c -> null);
    }

    /** The Ship holding most of these Grips, if at least {@link Grips#HELD}: the server's carry Ship rule, in slot order. */
    private static Frame carryShip(Level level, Grip[] grips) {
        ShipFrames ships = ShipFrames.get();
        List<Frame> frames = new ArrayList<>(grips.length);
        Frame best = null;
        int bestCount = Grips.HELD - 1;
        shipGrips = 0;
        for (Grip g : grips) {
            if (g == null) continue;
            Frame f = ships.isPlot(level, g.block()) ? ships.containing(level, g.block()) : null;
            frames.add(f);
            if (f == null) continue;
            shipGrips++;
            int n = 0;
            for (Frame o : frames) if (f.equals(o)) n++;
            if (n > bestCount) {
                bestCount = n;
                best = f;
            }
        }
        return best;
    }

    private static void setState(LocalPlayer player, RigState next) {
        state = next;
        if (next == RigState.STOWED) {
            GAIT.clear();
            motion = Vec3.ZERO;
            liftTo = Double.NaN;
            liftBlock = null;
            heldCount = 0;
            carryShip = null;
            shipGrips = 0;
            noRegrab = false;
            noFlying(player);
            RigVisuals.local(player, next, Tentacles.NONE);
            TentaworksNetwork.sendToServer(new RigMotionPacket(next, Vec3.ZERO, Tentacles.NONE));
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
        liftBlock = null;
        heldCount = 0;
        carryShip = null;
        shipGrips = 0;
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
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (TentaworksKeys.SETTINGS.consumeClick()) {
            if (mc.player != null && mc.screen == null && TentacleRigItem.isWorn(mc.player)) RigSettingsScreen.open(EquipmentSlot.CHEST);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        RigVisuals.clear();
    }

    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        reset();
        RigVisuals.clear();
    }
}
