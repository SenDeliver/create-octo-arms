package dev.tentaworks.dev;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Dev source set, Sable only (loaded only when Sable is): {@code /tentaworks ship <name> drive vx vy vz [wx wy wz]} and
 * {@code stop} (constant Ship motion for p9), and the SSHIP trace line of every overworld sub-level.
 * Drive: v in blocks/s, w in degrees/s about world axes through the rotation point. Each server tick the anchor pose
 * advances by v/20, w/20 and the body velocity is set to {@code v + GAIN·(anchor − pos)}, {@code w + GAIN·angle(anchor·q⁻¹)},
 * which also cancels gravity and drag.
 */
final class DevShips {
    private static final Logger LOG = LoggerFactory.getLogger("tentaworks-dev");
    private static final double GAIN = 5.0;
    private static final Map<String, Drive> DRIVES = new HashMap<>();
    private static boolean listening;

    private static final class Drive {
        final Vector3d v;
        final Vector3d w;
        final Vector3d pos;
        final Quaterniond q;

        Drive(Vector3d v, Vector3d w, Pose3dc pose) {
            this.v = v;
            this.w = w;
            this.pos = new Vector3d(pose.position());
            this.q = new Quaterniond(pose.orientation());
        }
    }

    private DevShips() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre e) -> drive(e.getServer()));
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
                if (DevTools.tracing()) trace(e.getServer());
            });
            NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> DRIVES.clear());
        }
        root.then(Commands.literal("ship").then(Commands.argument("name", StringArgumentType.word())
                .then(Commands.literal("stop").executes(c -> {
                    String name = StringArgumentType.getString(c, "name");
                    DRIVES.remove(name);
                    LOG.info("SHIPSTOP gt={} name={}", c.getSource().getLevel().getGameTime(), name);
                    return reply(c, "ship " + name + " stop");
                }))
                .then(Commands.literal("drive").then(Commands.argument("vx", DoubleArgumentType.doubleArg()).then(Commands.argument("vy", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("vz", DoubleArgumentType.doubleArg()).executes(c -> drive(c, false))
                                .then(Commands.argument("wx", DoubleArgumentType.doubleArg()).then(Commands.argument("wy", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("wz", DoubleArgumentType.doubleArg()).executes(c -> drive(c, true)))))))))));
    }

    private static int drive(CommandContext<CommandSourceStack> c, boolean spin) {
        String name = StringArgumentType.getString(c, "name");
        ServerSubLevel ship = named(c.getSource().getServer().overworld(), name);
        if (ship == null) return reply(c, "no ship " + name);
        Vector3d v = new Vector3d(DoubleArgumentType.getDouble(c, "vx"), DoubleArgumentType.getDouble(c, "vy"), DoubleArgumentType.getDouble(c, "vz"));
        Vector3d w = spin ? new Vector3d(DoubleArgumentType.getDouble(c, "wx"), DoubleArgumentType.getDouble(c, "wy"), DoubleArgumentType.getDouble(c, "wz")) : new Vector3d();
        DRIVES.put(name, new Drive(v, w, ship.logicalPose()));
        LOG.info("SHIPDRIVE gt={} name={} v={} w={}", c.getSource().getLevel().getGameTime(), name, fmt(v), fmt(w));
        return reply(c, "ship " + name + " drive");
    }

    private static void drive(MinecraftServer server) {
        if (DRIVES.isEmpty()) return;
        ServerLevel level = server.overworld();
        for (Iterator<Map.Entry<String, Drive>> it = DRIVES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Drive> e = it.next();
            ServerSubLevel ship = named(level, e.getKey());
            if (ship == null) {
                it.remove();
                continue;
            }
            Drive d = e.getValue();
            d.pos.fma(1 / 20.0, d.v);
            double rate = Math.toRadians(d.w.length());
            if (rate > 0) d.q.premul(new Quaterniond().rotateAxis(rate / 20, d.w.x / d.w.length(), d.w.y / d.w.length(), d.w.z / d.w.length())).normalize();
            RigidBodyHandle body = RigidBodyHandle.of(ship);
            if (body == null || !body.isValid()) continue;
            Pose3dc pose = ship.logicalPose();
            Vector3d targetV = new Vector3d(d.pos).sub(pose.position()).mul(GAIN).add(d.v);
            Quaterniond err = new Quaterniond(d.q).mul(new Quaterniond(pose.orientation()).conjugate());
            double sign = err.w < 0 ? -1 : 1;
            Vector3d axis = new Vector3d(err.x, err.y, err.z).mul(sign);
            double len = axis.length();
            double k = len < 1.0E-9 ? 2 : 2 * Math.atan2(len, Math.abs(err.w)) / len;
            Vector3d targetW = axis.mul(k * GAIN).add(new Vector3d(d.w).mul(Math.PI / 180));
            body.addLinearAndAngularVelocity(targetV.sub(body.getLinearVelocity(new Vector3d())), targetW.sub(body.getAngularVelocity(new Vector3d())));
        }
    }

    private static void trace(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        long gt = level.getGameTime();
        for (ServerSubLevel s : container.getAllSubLevels()) {
            if (s.isRemoved()) continue;
            Pose3dc p = s.logicalPose();
            RigidBodyHandle body = RigidBodyHandle.of(s);
            boolean live = body != null && body.isValid();
            Vector3d v = live ? body.getLinearVelocity(new Vector3d()) : new Vector3d();
            Vector3d w = live ? body.getAngularVelocity(new Vector3d()).mul(180 / Math.PI) : new Vector3d();
            var q = p.orientation();
            LOG.info("SSHIP gt={} name={} id={} pos={} q={} v={} w={}", gt, s.getName() == null || s.getName().isEmpty() ? "-" : s.getName(), s.getUniqueId(),
                    fmt(new Vector3d(p.position())), String.format("%.5f,%.5f,%.5f,%.5f", q.x(), q.y(), q.z(), q.w()), fmt(v), fmt(w));
        }
    }

    private static ServerSubLevel named(ServerLevel level, String name) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        for (ServerSubLevel s : container.getAllSubLevels()) if (!s.isRemoved() && name.equals(s.getName())) return s;
        return null;
    }

    private static String fmt(Vector3d v) {
        return String.format("%.3f,%.3f,%.3f", v.x, v.y, v.z);
    }

    private static int reply(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
