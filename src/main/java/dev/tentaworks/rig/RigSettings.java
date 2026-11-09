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
import java.util.Optional;

/**
 * The player's Rig Settings, stored on each Rig item (component {@code tentaworks:rig_settings}). Values are clamped to
 * the Server Config whenever they are written and again whenever they are read, since an admin can lower the caps later.
 * A Speed never set (NaN, not saved) reads as the server's top Speed.
 */
public record RigSettings(float standingHeight, float speed, ActivationMode activation, FarUseMode farUse) {
    public static final float DEFAULT_HEIGHT = 1.5F;
    public static final float UNSET_SPEED = Float.NaN;
    public static final float WALK_SPEED = 4.3F;
    public static final RigSettings DEFAULT = new RigSettings(DEFAULT_HEIGHT, UNSET_SPEED, ActivationMode.DOUBLE_JUMP, FarUseMode.ALWAYS);

    public static final Codec<RigSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.lenientOptionalFieldOf("standing_height", DEFAULT_HEIGHT).forGetter(RigSettings::standingHeight),
            Codec.FLOAT.lenientOptionalFieldOf("speed").xmap(o -> o.orElse(UNSET_SPEED), (Float f) -> Float.isFinite(f) ? Optional.of(f) : Optional.<Float>empty())
                    .forGetter(RigSettings::speed),
            ActivationMode.CODEC.lenientOptionalFieldOf("activation", ActivationMode.DOUBLE_JUMP).forGetter(RigSettings::activation),
            FarUseMode.CODEC.lenientOptionalFieldOf("far_use", FarUseMode.ALWAYS).forGetter(RigSettings::farUse)
    ).apply(i, RigSettings::new));
    public static final StreamCodec<ByteBuf, RigSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, RigSettings::standingHeight,
            ByteBufCodecs.FLOAT, RigSettings::speed,
            ActivationMode.STREAM_CODEC, RigSettings::activation,
            FarUseMode.STREAM_CODEC, RigSettings::farUse,
            RigSettings::new);

    public RigSettings(float standingHeight, float speed, ActivationMode activation) {
        this(standingHeight, speed, activation, FarUseMode.ALWAYS);
    }

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

    /** When the Right Actuator lends either hand the Rig's Reach (Far Use). */
    public enum FarUseMode implements StringRepresentable {
        ALWAYS,
        RIG_OUT,
        OFF;

        public static final Codec<FarUseMode> CODEC = StringRepresentable.fromEnum(FarUseMode::values);
        public static final StreamCodec<ByteBuf, FarUseMode> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(FarUseMode::byName, FarUseMode::getSerializedName);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        static FarUseMode byName(String name) {
            for (FarUseMode m : values()) if (m.getSerializedName().equals(name)) return m;
            return ALWAYS;
        }
    }

    public static RigSettings of(ItemStack rig) {
        RigSettings raw = rig.getOrDefault(TentaworksDataComponents.RIG_SETTINGS.get(), DEFAULT);
        return raw.clamped(rig.getItem() instanceof TentacleRigItem item ? item.reach(rig) : TentacleRigItem.MAX_REACH);
    }

    public static float maxSpeed() {
        return (float) TentaworksConfig.value(TentaworksConfig.SPEED_MAX);
    }

    public static float minSpeed() {
        return Math.min(WALK_SPEED, maxSpeed());
    }

    public RigSettings clamped(double reach) {
        float h = Float.isFinite(standingHeight) ? standingHeight : DEFAULT_HEIGHT;
        float s = Float.isFinite(speed) ? speed : maxSpeed();
        return new RigSettings(Mth.clamp(tenths(h), 0, (float) reach), Mth.clamp(tenths(s), minSpeed(), maxSpeed()),
                activation == null ? ActivationMode.DOUBLE_JUMP : activation, farUse == null ? FarUseMode.ALWAYS : farUse);
    }

    private static float tenths(float v) {
        return Math.round(v * 10) / 10.0F;
    }
}
