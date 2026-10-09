package dev.tentaworks.rig;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * The four Tentacles of a Rig on the wire, by slot (front left, front right, back left, back right): each slot's Grip
 * (null = hanging free) and its Step phase (255 = planted, lower = still arcing toward that Grip).
 */
public record Tentacles(Grip[] grips, byte[] phases) {
    public static final int SLOTS = 4;
    public static final Tentacles NONE = new Tentacles(new Grip[SLOTS], new byte[]{-1, -1, -1, -1});

    public static final StreamCodec<ByteBuf, Tentacles> STREAM_CODEC = StreamCodec.of(
            (buf, t) -> {
                for (int i = 0; i < SLOTS; i++) {
                    Grip g = t.grips[i];
                    buf.writeBoolean(g != null);
                    if (g != null) {
                        Grip.STREAM_CODEC.encode(buf, g);
                        buf.writeByte(t.phases[i]);
                    }
                }
            },
            buf -> {
                Grip[] grips = new Grip[SLOTS];
                byte[] phases = {-1, -1, -1, -1};
                for (int i = 0; i < SLOTS; i++) {
                    if (!buf.readBoolean()) continue;
                    grips[i] = Grip.STREAM_CODEC.decode(buf);
                    phases[i] = buf.readByte();
                }
                return new Tentacles(grips, phases);
            });

    public static byte phaseByte(float phase) {
        return (byte) Math.round(Math.max(0, Math.min(1, phase)) * 255);
    }

    public float phase(int slot) {
        return (phases[slot] & 0xFF) / 255f;
    }

    public List<Grip> list() {
        List<Grip> out = new ArrayList<>(SLOTS);
        for (Grip g : grips) if (g != null) out.add(g);
        return out;
    }

    public Tentacles only(Grip[] kept) {
        return new Tentacles(kept, phases);
    }
}
