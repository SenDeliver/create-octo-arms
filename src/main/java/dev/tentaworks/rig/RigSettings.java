package dev.tentaworks.rig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.tentaworks.config.TentaworksConfig;
import dev.tentaworks.registry.TentaworksDataComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * The player's Rig Settings, stored on each Rig item (component {@code tentaworks:rig_settings}). Values are clamped to
 * the Server Config whenever they are written and again whenever they are read, since an admin can lower the caps later.
 */
public record RigSettings(float standingHeight, float speed, ActivationMode activation) {
    public static final float DEFAULT_HEIGHT = 1.5F;
    public static final float DEFAULT_SPEED = 5.6F;
    public static final float WALK_SPEED = 4.3F;
    public static final RigSettings DEFAULT = new RigSettings(DEFAULT_HEIGHT, DEFAULT_SPEED, ActivationMode.DOUBLE_JUMP);

    public static final Codec<RigSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.lenientOptionalFieldOf("standing_height", DEFAULT_HEIGHT).forGetter(RigSettings::standingHeight),
            Codec.FLOAT.lenientOptionalFieldOf("speed", DEFAULT_SPEED).forGetter(RigSettings::speed),
            ActivationMode.CODEC.lenientOptionalFieldOf("activation", ActivationMode.DOUBLE_JUMP).forGetter(RigSettings::activation)
    ).apply(i, RigSettings::new));
    public static final StreamCodec<ByteBuf, RigSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, RigSettings::standingHeight,
            ByteBufCodecs.FLOAT, RigSettings::speed,
            ActivationMode.STREAM_CODEC, RigSettings::activation,
            RigSettings::new);

    public enum ActivationMode implements StringRepresentable {
        DOUBLE_JUMP,
        KEY;

        public static final Codec<ActivationMode> CODEC = StringRepresentable.fromEnum(ActivationMode::values);
        public static final StreamCodec<ByteBuf, ActivationMode> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(ActivationMode::byName, ActivationMode::getSerializedName);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        static ActivationMode byName(String name) {
            for (ActivationMode m : values()) if (m.getSerializedName().equals(name)) return m;
            return DOUBLE_JUMP;
        }
    }

    public static RigSettings of(ItemStack rig) {
        RigSettings raw = rig.getOrDefault(TentaworksDataComponents.RIG_SETTINGS.get(), DEFAULT);
        return rig.getItem() instanceof TentacleRigItem item ? raw.clamped(item.reach(rig)) : raw;
    }

    public static float maxSpeed() {
        return (float) TentaworksConfig.value(TentaworksConfig.SPEED_MAX);
    }

    public static float minSpeed() {
        return Math.min(WALK_SPEED, maxSpeed());
    }

    public RigSettings clamped(double reach) {
        float h = Float.isFinite(standingHeight) ? standingHeight : DEFAULT_HEIGHT;
        float s = Float.isFinite(speed) ? speed : DEFAULT_SPEED;
        return new RigSettings(Mth.clamp(tenths(h), 0, (float) reach), Mth.clamp(tenths(s), minSpeed(), maxSpeed()),
                activation == null ? ActivationMode.DOUBLE_JUMP : activation);
    }

    private static float tenths(float v) {
        return Math.round(v * 10) / 10.0F;
    }
}
