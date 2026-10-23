package dev.tentaworks.rig;

import dev.tentaworks.TentaworksIds;
import dev.tentaworks.config.TentaworksConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The player's Rig Settings, stored on each Rig item (stack tag {@code tentaworks:rig_settings}). Values are clamped to
 * the Server Config whenever they are written and again whenever they are read, since an admin can lower the caps later.
 */
public record RigSettings(float standingHeight, float speed, ActivationMode activation) {
    public static final float DEFAULT_HEIGHT = 1.5F;
    public static final float DEFAULT_SPEED = 5.6F;
    public static final float WALK_SPEED = 4.3F;
    public static final RigSettings DEFAULT = new RigSettings(DEFAULT_HEIGHT, DEFAULT_SPEED, ActivationMode.DOUBLE_JUMP);

    public static final String NBT_KEY = TentaworksIds.MOD_ID + ":" + TentaworksIds.RIG_SETTINGS;
    private static final String HEIGHT_KEY = "standing_height";
    private static final String SPEED_KEY = "speed";
    private static final String ACTIVATION_KEY = "activation";

    public enum ActivationMode implements StringRepresentable {
        DOUBLE_JUMP,
        KEY;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        static ActivationMode byName(String name) {
            for (ActivationMode m : values()) if (m.getSerializedName().equals(name)) return m;
            return DOUBLE_JUMP;
        }
    }

    /** Each field on its own: a missing or malformed field falls back to its default and keeps the others. */
    public static RigSettings fromNbt(CompoundTag tag) {
        float h = tag.contains(HEIGHT_KEY, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(HEIGHT_KEY) : DEFAULT_HEIGHT;
        float s = tag.contains(SPEED_KEY, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(SPEED_KEY) : DEFAULT_SPEED;
        ActivationMode a = tag.contains(ACTIVATION_KEY, Tag.TAG_STRING) ? ActivationMode.byName(tag.getString(ACTIVATION_KEY)) : ActivationMode.DOUBLE_JUMP;
        return new RigSettings(h, s, a);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat(HEIGHT_KEY, standingHeight);
        tag.putFloat(SPEED_KEY, speed);
        tag.putString(ACTIVATION_KEY, (activation == null ? ActivationMode.DOUBLE_JUMP : activation).getSerializedName());
        return tag;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(standingHeight);
        buf.writeFloat(speed);
        buf.writeUtf((activation == null ? ActivationMode.DOUBLE_JUMP : activation).getSerializedName());
    }

    public static RigSettings read(FriendlyByteBuf buf) {
        return new RigSettings(buf.readFloat(), buf.readFloat(), ActivationMode.byName(buf.readUtf()));
    }

    /** The settings saved on the stack as they are (not clamped), or null when it has none. */
    @Nullable
    public static RigSettings stored(ItemStack rig) {
        CompoundTag tag = rig.getTag();
        return tag != null && tag.contains(NBT_KEY, Tag.TAG_COMPOUND) ? fromNbt(tag.getCompound(NBT_KEY)) : null;
    }

    public static void set(ItemStack rig, RigSettings settings) {
        rig.getOrCreateTag().put(NBT_KEY, settings.toNbt());
    }

    public static RigSettings of(ItemStack rig) {
        RigSettings stored = stored(rig);
        RigSettings raw = stored == null ? DEFAULT : stored;
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
