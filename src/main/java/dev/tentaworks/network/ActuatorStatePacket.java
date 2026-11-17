package dev.tentaworks.network;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.rig.HoldArm;
import dev.tentaworks.rig.ShipBuild;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → clients: what the Actuators of player {@code entityId} hold. {@code heldId} the held mob (-1 none), {@code heavy}
 * when both Actuators hold it, {@code ship} the Ship block held by {@code shipFace} (null none), {@code arm} which Actuator holds,
 * {@code distance} the hold distance along the look (blocks, 0 none), {@code overhead} a Ship held Overhead, {@code shipTangent} the
 * in-face direction of the held face the two tips took hold along at the Grab (its Frame, toward the wearer's right), {@code left} and
 * {@code right} the body-left and body-right tips on a held Ship ({@link Tip}), {@code build} the ids of the held Ship's build
 * (the held Ship first, then the Ships touching it: no Grip on them holds the wearer up, {@code ShipBuild}; empty for a mob), {@code speed}
 * the wearer's walking and Rig Speed factor while holding ({@code grab.heavySpeedFactor} for a heavy mob, times the Build's
 * {@code ShipWeight} walk factor for a Ship; 1 for a light mob or nothing). To the wearer after every
 * Grab request, with {@code refusal} the lang key of the action-bar line when it was refused (empty otherwise); to trackers on
 * every change and on start of tracking.
 */
public record ActuatorStatePacket(int entityId, int heldId, boolean heavy, @Nullable BlockPos ship, Direction shipFace, String refusal,
                                  HoldArm arm, float distance, boolean overhead, Direction shipTangent, Tip left, Tip right, List<UUID> build,
                                  double speed)
        implements CustomPacketPayload {
    /**
     * One tip on a held Ship: where it holds ({@code at}) or reaches to, and where it reached from, both in the Ship's plot space
     * relative to the held block (plot coordinates are too large for a float); {@code reach} ticks into a reach, −1 holding.
     */
    public record Tip(Vec3 at, Vec3 from, int reach) {
        public static final Tip NONE = new Tip(Vec3.ZERO, Vec3.ZERO, -1);

        public boolean holding() {
            return reach < 0;
        }

        static void write(FriendlyByteBuf buf, Tip t) {
            vec(buf, t.at);
            buf.writeByte(Math.max(-1, Math.min(126, t.reach)));
            if (t.reach >= 0) vec(buf, t.from);
        }

        static Tip read(FriendlyByteBuf buf) {
            Vec3 at = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
            int reach = buf.readByte();
            return new Tip(at, reach >= 0 ? new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()) : at, reach);
        }

        private static void vec(FriendlyByteBuf buf, Vec3 v) {
            buf.writeFloat((float) v.x);
            buf.writeFloat((float) v.y);
            buf.writeFloat((float) v.z);
        }
    }


    public static final Type<ActuatorStatePacket> TYPE = new Type<>(TentaworksIds.id("actuator_state"));
    private static final int MAX_REFUSAL = 256;
    private static final int MAX_BUILD = ShipBuild.MAX;
    public static final StreamCodec<FriendlyByteBuf, ActuatorStatePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeVarInt(p.heldId + 1);
                buf.writeBoolean(p.heavy);
                buf.writeBoolean(p.ship != null);
                if (p.ship != null) buf.writeBlockPos(p.ship);
                buf.writeByte(p.shipFace.get3DDataValue());
                buf.writeUtf(p.refusal, MAX_REFUSAL);
                buf.writeByte(p.arm.ordinal());
                buf.writeFloat(p.distance);
                buf.writeBoolean(p.overhead);
                buf.writeByte(p.shipTangent.get3DDataValue());
                Tip.write(buf, p.left);
                Tip.write(buf, p.right);
                buf.writeVarInt(Math.min(p.build.size(), MAX_BUILD));
                for (int i = 0; i < Math.min(p.build.size(), MAX_BUILD); i++) buf.writeUUID(p.build.get(i));
                buf.writeDouble(p.speed);
            },
            buf -> {
                int entity = buf.readVarInt();
                int held = buf.readVarInt() - 1;
                boolean heavy = buf.readBoolean();
                BlockPos ship = buf.readBoolean() ? buf.readBlockPos() : null;
                Direction face = Direction.from3DDataValue(buf.readByte());
                String refusal = buf.readUtf(MAX_REFUSAL);
                HoldArm arm = HoldArm.byId(buf.readByte());
                float distance = buf.readFloat();
                boolean overhead = buf.readBoolean();
                Direction tangent = Direction.from3DDataValue(buf.readByte());
                Tip left = Tip.read(buf), right = Tip.read(buf);
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_BUILD) throw new IllegalArgumentException("Ship build of " + n);
                List<UUID> build = new ArrayList<>(n);
                for (int i = 0; i < n; i++) build.add(buf.readUUID());
                double speed = buf.readDouble();
                if (!(speed > 0 && speed <= 1)) throw new IllegalArgumentException("speed factor " + speed);
                return new ActuatorStatePacket(entity, held, heavy, ship, face, refusal, arm, distance, overhead, tangent, left, right, List.copyOf(build), speed);
            });

    public static ActuatorStatePacket empty(int entityId, String refusal) {
        return new ActuatorStatePacket(entityId, -1, false, null, Direction.UP, refusal, HoldArm.NONE, 0, false, Direction.NORTH, Tip.NONE, Tip.NONE, List.of(), 1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
