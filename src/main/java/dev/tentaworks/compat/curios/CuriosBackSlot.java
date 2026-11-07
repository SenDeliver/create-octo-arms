package dev.tentaworks.compat.curios;

import dev.tentaworks.compat.BackSlot;
import dev.tentaworks.rig.TentacleRigItem;
import dev.tentaworks.rig.WornRig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioCanEquipEvent;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Curios 9 (NeoForge 1.21.1) back slot: the only class besides {@link CuriosClient} that touches Curios. Loaded only when
 * Curios is. Players get the slot from {@code data/tentaworks/curios/entities/back.json}, the Rigs fit it through the
 * {@code curios:back} item tag. Air needs nothing here: Create's own Curios compat already registers every curio stack tagged
 * {@code create:pressurized_air_sources} as a Backtank supplier, so the Diving Helmet and the air HUD draw on a Back Slot Rig.
 * Mending needs nothing either: Curios mends worn curios on XP pickup.
 */
public final class CuriosBackSlot implements BackSlot {
    public static final String SLOT = "back";
    /** A refusal is said once a second per player: the equip check runs on every menu click and hover. */
    private static final int SAY_EVERY = 20;
    private static final Map<UUID, Integer> SAID = new WeakHashMap<>();

    private CuriosBackSlot() {
    }

    public static BackSlot create() {
        NeoForge.EVENT_BUS.addListener(CuriosBackSlot::onCanEquip);
        NeoForge.EVENT_BUS.addListener((CurioChangeEvent e) -> WornRig.forget(e.getEntity()));
        return new CuriosBackSlot();
    }

    @Override
    public ItemStack find(LivingEntity entity, Predicate<ItemStack> filter) {
        return CuriosApi.getCuriosInventory(entity).flatMap(inv -> inv.getStacksHandler(SLOT))
                .map(h -> first(h.getStacks(), filter, -1)).orElse(ItemStack.EMPTY);
    }

    /** Through Curios' own validity check, so the one-Rig rule and the config apply here too. */
    @Override
    public boolean insert(LivingEntity entity, ItemStack stack) {
        return CuriosApi.getCuriosInventory(entity).flatMap(inv -> inv.getStacksHandler(SLOT)).map(h -> {
            IDynamicStackHandler stacks = h.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (!stacks.getStackInSlot(i).isEmpty() || !stacks.isItemValid(i, stack)) continue;
                stacks.setStackInSlot(i, stack);
                WornRig.forget(entity);
                return true;
            }
            return false;
        }).orElse(false);
    }

    private static ItemStack first(IDynamicStackHandler stacks, Predicate<ItemStack> filter, int skip) {
        for (int i = 0; i < stacks.getSlots(); i++) {
            ItemStack s = stacks.getStackInSlot(i);
            if (i != skip && !s.isEmpty() && filter.test(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    /**
     * A Rig fits the back slot only (never the generic {@code curio} slot, never a cosmetic one), one at a time: refused while the
     * chest slot or another back slot holds a Rig, and refused altogether while the server config has the Back Slot off.
     */
    private static void onCanEquip(CurioCanEquipEvent event) {
        SlotContext ctx = event.getSlotContext();
        if (!(event.getStack().getItem() instanceof TentacleRigItem) || ctx.entity() == null) return;
        if (!SLOT.equals(ctx.identifier()) || ctx.cosmetic()) {
            event.setEquipResult(TriState.FALSE);
            return;
        }
        if (!WornRig.backSlotAllowed()) {
            refuse(event, ctx, "tentaworks.rig.back.refused_off");
            return;
        }
        boolean other = CuriosApi.getCuriosInventory(ctx.entity()).flatMap(inv -> inv.getStacksHandler(SLOT))
                .map(h -> !first(h.getStacks(), s -> s.getItem() instanceof TentacleRigItem, ctx.index()).isEmpty()).orElse(false);
        if (other || WornRig.chestRig(ctx.entity())) refuse(event, ctx, "tentaworks.rig.one_at_a_time");
    }

    private static void refuse(CurioCanEquipEvent event, SlotContext ctx, String key) {
        event.setEquipResult(TriState.FALSE);
        if (!(ctx.entity() instanceof ServerPlayer player)) return;
        Integer last = SAID.get(player.getUUID());
        if (last != null && player.tickCount - last < SAY_EVERY) return;
        SAID.put(player.getUUID(), player.tickCount);
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }
}
