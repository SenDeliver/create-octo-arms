package dev.tentaworks.rig;

import dev.tentaworks.compat.BackSlot;
import dev.tentaworks.compat.NoBackSlot;
import dev.tentaworks.config.TentaworksConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Rig a player wears, and where (adr/0006): the chest slot, else the Curios Back Slot. One Rig at a time; when both
 * slots hold one anyway (commands, dispensers, other mods), the chest Rig wins and the Back Slot Rig is inert. With the
 * server config {@code curios.backSlot} off, a Back Slot Rig is inert too.
 */
public record WornRig(ItemStack stack, @Nullable RigSlot slot) {
    public static final WornRig NONE = new WornRig(ItemStack.EMPTY, null);
    /** The Back Slot lookup per entity and tick, one map per side (the integrated server runs beside the client). */
    private static final Map<LivingEntity, Back> SERVER_BACK = new WeakHashMap<>();
    private static final Map<LivingEntity, Back> CLIENT_BACK = new WeakHashMap<>();

    private record Back(int tick, ItemStack stack) {
    }

    public static WornRig of(LivingEntity entity) {
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof TentacleRigItem) return new WornRig(chest, RigSlot.CHEST);
        if (!backSlotAllowed()) return NONE;
        ItemStack back = backRig(entity);
        return back.isEmpty() ? NONE : new WornRig(back, RigSlot.BACK);
    }

    /** Curios is present (the Back Slot exists at all). */
    public static boolean backSlotPresent() {
        return BackSlot.get() != NoBackSlot.INSTANCE;
    }

    /** Curios is present and the server config lets Rigs work from the Back Slot. */
    public static boolean backSlotAllowed() {
        return backSlotPresent() && TentaworksConfig.value(TentaworksConfig.BACK_SLOT);
    }

    /**
     * The Rig in the entity's Back Slot, worn or inert, or EMPTY (always EMPTY without Curios; the config does not matter here).
     * Asked many times a tick (hot paths on both sides), so Curios is asked once per entity tick; a Curios change drops the entry ({@link #forget}).
     */
    public static ItemStack backRig(LivingEntity entity) {
        BackSlot slots = BackSlot.get();
        if (slots == NoBackSlot.INSTANCE) return ItemStack.EMPTY;
        Map<LivingEntity, Back> cache = entity.level().isClientSide() ? CLIENT_BACK : SERVER_BACK;
        Back b = cache.get(entity);
        if (b != null && b.tick == entity.tickCount) return b.stack.isEmpty() ? ItemStack.EMPTY : b.stack;
        ItemStack s = slots.find(entity, st -> st.getItem() instanceof TentacleRigItem);
        cache.put(entity, new Back(entity.tickCount, s));
        return s;
    }

    /** The entity's Back Slot changed: the next lookup asks Curios again. */
    public static void forget(LivingEntity entity) {
        (entity.level().isClientSide() ? CLIENT_BACK : SERVER_BACK).remove(entity);
    }

    public static boolean chestRig(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof TentacleRigItem;
    }

    public boolean worn() {
        return slot != null;
    }

    public boolean inBack() {
        return slot == RigSlot.BACK;
    }

    @Nullable
    public TentacleRigItem item() {
        return stack.getItem() instanceof TentacleRigItem rig ? rig : null;
    }

    /**
     * A Back Slot Rig wears from hits as chest armor would ({@code Player.hurtArmor}: only damage armor stops, at least 1
     * per hit, a quarter of the damage), so "worn out cannot Rise" holds there too. It never breaks ({@link TentacleRigItem#damageItem}).
     */
    public static void wearBack(LivingEntity wearer, DamageSource source, float damage) {
        if (damage <= 0 || !(wearer.level() instanceof ServerLevel level)) return;
        WornRig w = of(wearer);
        if (!w.inBack() || !w.stack.canBeHurtBy(source)) return;
        w.stack.hurtAndBreak((int) Math.max(1.0F, damage / 4.0F), level, wearer, item -> {
        });
    }
}
