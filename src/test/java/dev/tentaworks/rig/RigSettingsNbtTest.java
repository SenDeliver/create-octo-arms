package dev.tentaworks.rig;

import dev.tentaworks.registry.TentaworksItems;
import dev.tentaworks.rig.RigSettings.ActivationMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Rig Settings in the stack tag {@code tentaworks:rig_settings} (docs/save-format.md). Each field is read on its own (CR12):
 * a missing or malformed field falls back to its default and keeps the others, as the 1.21 lenient codec did. Runs in the
 * dev server (fmlTest) for the Rig stacks.
 */
class RigSettingsNbtTest {
    private static final RigSettings CUSTOM = new RigSettings(2.3F, 6.1F, ActivationMode.KEY);

    private static CompoundTag custom() {
        return CUSTOM.toNbt();
    }

    @Test
    void frozenNames() {
        assertEquals("tentaworks:rig_settings", RigSettings.NBT_KEY);
        CompoundTag tag = custom();
        assertEquals(3, tag.size());
        assertEquals(Tag.TAG_FLOAT, tag.getTagType("standing_height"));
        assertEquals(Tag.TAG_FLOAT, tag.getTagType("speed"));
        assertEquals("key", tag.getString("activation"));
        assertEquals("double_jump", RigSettings.DEFAULT.toNbt().getString("activation"));
    }

    @Test
    void roundTrip() {
        assertEquals(CUSTOM, RigSettings.fromNbt(custom()));
        assertEquals(RigSettings.DEFAULT, RigSettings.fromNbt(RigSettings.DEFAULT.toNbt()));
    }

    @Test
    void nonFiniteRoundTrips() {
        RigSettings odd = new RigSettings(Float.NaN, Float.POSITIVE_INFINITY, ActivationMode.KEY);
        RigSettings back = RigSettings.fromNbt(odd.toNbt());
        assertEquals(Float.NaN, back.standingHeight());
        assertEquals(Float.POSITIVE_INFINITY, back.speed());
    }

    @Test
    void missingTagGivesDefaults() {
        assertEquals(RigSettings.DEFAULT, RigSettings.fromNbt(new CompoundTag()));
    }

    @Test
    void missingFieldKeepsOthers() {
        CompoundTag tag = custom();
        tag.remove("speed");
        assertEquals(new RigSettings(2.3F, RigSettings.DEFAULT_SPEED, ActivationMode.KEY), RigSettings.fromNbt(tag));
    }

    @Test
    void badHeightKeepsOthers() {
        CompoundTag tag = custom();
        tag.putString("standing_height", "tall");
        assertEquals(new RigSettings(RigSettings.DEFAULT_HEIGHT, 6.1F, ActivationMode.KEY), RigSettings.fromNbt(tag));
    }

    @Test
    void badSpeedKeepsOthers() {
        CompoundTag tag = custom();
        tag.put("speed", new CompoundTag());
        assertEquals(new RigSettings(2.3F, RigSettings.DEFAULT_SPEED, ActivationMode.KEY), RigSettings.fromNbt(tag));
    }

    @Test
    void activationAsNumberKeepsOthers() {
        CompoundTag tag = custom();
        tag.putInt("activation", 1);
        assertEquals(new RigSettings(2.3F, 6.1F, ActivationMode.DOUBLE_JUMP), RigSettings.fromNbt(tag));
    }

    @Test
    void unknownActivationKeepsOthers() {
        CompoundTag tag = custom();
        tag.putString("activation", "KEY");
        assertEquals(new RigSettings(2.3F, 6.1F, ActivationMode.DOUBLE_JUMP), RigSettings.fromNbt(tag));
        tag.putString("activation", "sideways");
        assertEquals(new RigSettings(2.3F, 6.1F, ActivationMode.DOUBLE_JUMP), RigSettings.fromNbt(tag));
    }

    @Test
    void anyNumberType() {
        CompoundTag tag = custom();
        tag.putInt("standing_height", 2);
        tag.putDouble("speed", 7.5);
        assertEquals(new RigSettings(2.0F, 7.5F, ActivationMode.KEY), RigSettings.fromNbt(tag));
    }

    @Test
    void activationNames() {
        assertEquals("double_jump", ActivationMode.DOUBLE_JUMP.getSerializedName());
        assertEquals("key", ActivationMode.KEY.getSerializedName());
        for (ActivationMode m : ActivationMode.values()) assertEquals(m, ActivationMode.byName(m.getSerializedName()));
    }

    @Test
    void stackWithoutSettings() {
        ItemStack rig = new ItemStack(TentaworksItems.COPPER_TENTACLE_RIG.get());
        assertNull(RigSettings.stored(rig));
        rig.getOrCreateTag().putFloat("Air", 300);
        assertNull(RigSettings.stored(rig));
        TentacleRigItem item = TentaworksItems.COPPER_TENTACLE_RIG.get();
        assertEquals(RigSettings.DEFAULT.clamped(item.reach(rig)), RigSettings.of(rig));
    }

    @Test
    void setKeepsTheRestOfTheTag() {
        ItemStack rig = new ItemStack(TentaworksItems.NETHERITE_TENTACLE_RIG.get());
        rig.getOrCreateTag().putFloat("Air", 300);
        RigSettings.set(rig, CUSTOM);
        assertEquals(300F, rig.getTag().getFloat("Air"));
        assertEquals(Tag.TAG_COMPOUND, rig.getTag().getTagType("tentaworks:rig_settings"));
        assertEquals(CUSTOM, RigSettings.stored(rig));
        assertEquals(CUSTOM, RigSettings.stored(rig.copy()));
    }

    @Test
    void settingsTagNotACompound() {
        ItemStack rig = new ItemStack(TentaworksItems.COPPER_TENTACLE_RIG.get());
        rig.getOrCreateTag().putString("tentaworks:rig_settings", "broken");
        assertNull(RigSettings.stored(rig));
    }
}
