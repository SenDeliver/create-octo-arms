package dev.tentaworks.rig;

import com.simibubi.create.content.equipment.armor.BacktankItem;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.foundation.item.LayeredArmorItem;
import dev.tentaworks.client.RigSettingsScreen;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.stats.Stats;
import dev.tentaworks.compat.BackSlot;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A Backtank fitted with Tentacles (adr/0002): Create's air, diving, Capacity and back rendering apply unchanged.
 * Worn in the chest slot, or in the Curios Back Slot as a Rig that is not armor (adr/0006, {@link WornRig}).
 */
public class TentacleRigItem extends BacktankItem {
    public static final double MAX_REACH = 16.0;
    private final RigTier tier;

    public TentacleRigItem(Holder<ArmorMaterial> material, Properties properties, ResourceLocation textureLoc,
                           Supplier<BacktankBlockItem> placeable, RigTier tier) {
        super(material, properties, textureLoc, placeable);
        this.tier = tier;
    }

    public RigTier tier() {
        return tier;
    }

    @Nullable
    public static RigTier wornTier(Player player) {
        TentacleRigItem rig = WornRig.of(player).item();
        return rig == null ? null : rig.tier;
    }

    public static boolean isWorn(Player player) {
        return wornTier(player) != null;
    }

    public static double reach(Player player) {
        ItemStack worn = WornRig.of(player).stack();
        return worn.getItem() instanceof TentacleRigItem rig ? rig.reach(worn) : 0;
    }

    /** Tier Reach plus Far Reach, never above the config's Reach cap. */
    public double reach(ItemStack stack) {
        return Math.min(MAX_REACH, tier.reach() + RigEnchants.farReach(stack));
    }

    /** Top Speed of this Rig in blocks per second: {@code speed.max} with Swift. */
    public static double topSpeed(ItemStack stack) {
        return RigSettings.maxSpeed() * RigEnchants.swift(stack);
    }

    /** Create's BacktankItem refuses Mending and Unbreaking (its backtanks have no durability); a Rig has durability, so it takes them. */
    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return stack.is(Items.ENCHANTED_BOOK) || enchantment.value().isSupportedItem(stack);
    }

    /** Elytra-style: at 1 durability the Rig cannot be Activated (an Active one Sets Down) until repaired. */
    public static boolean wornOut(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /** The Rig never breaks: wear stops at 1 durability, so Air, enchantments and Rig Settings stay with it. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
        return amount <= 0 ? amount : Math.max(0, Math.min(amount, stack.getMaxDamage() - 1 - stack.getDamageValue()));
    }

    @Override
    public int getDamage(ItemStack stack) {
        return Math.min(super.getDamage(stack), Math.max(0, stack.getMaxDamage() - 1));
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        super.setDamage(stack, Math.min(damage, Math.max(0, stack.getMaxDamage() - 1)));
    }

    /** The item bar shows Air, so wear is shown here once the Rig is damaged (advanced tooltips already show it). */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tentaworks.stats.air").withStyle(ChatFormatting.GRAY).append(" ")
                .append(Component.translatable("tentaworks.stats.air.value", BacktankUtil.getAir(stack), BacktankUtil.maxAir(stack)).withStyle(ChatFormatting.GOLD)));
        RigStats.addRigLines(stack, tooltip, "");
        if (RigEnchants.deft(stack) && !Actuators.fitted(stack))
            tooltip.add(Component.translatable("tentaworks.rig.deft_idle").withStyle(ChatFormatting.DARK_GRAY));
        if (stack.isDamaged() && !flag.isAdvanced())
            tooltip.add(Component.translatable("item.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()).withStyle(ChatFormatting.GRAY));
        if (wornOut(stack))
            tooltip.add(Component.translatable("tentaworks.rig.worn_out", tier.repairItem().getDescription()).withStyle(ChatFormatting.RED));
        else if (flag.isAdvanced() && WornRig.backSlotPresent())
            tooltip.add(Component.translatable("tentaworks.rig.wears_any_slot").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Armor enchantments idle in the Back Slot (adr/0006): Protection and its kinds, Thorns, anything that is not a Rig or tool enchantment. */
    public static boolean hasArmorEnchant(ItemStack stack) {
        for (Holder<Enchantment> e : stack.getEnchantments().keySet()) {
            if (!RigEnchants.isRigEnchant(e) && !e.is(Enchantments.MENDING) && !e.is(Enchantments.UNBREAKING)
                    && !e.is(Enchantments.BINDING_CURSE) && !e.is(Enchantments.VANISHING_CURSE)) return true;
        }
        return false;
    }

    /** One Rig at a time (adr/0006): no Rig into the chest slot while the Back Slot holds one (armor slot, shift-click, dispenser). */
    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, LivingEntity entity) {
        return super.canEquip(stack, armorType, entity) && !(armorType == SLOT && WornRig.backSlotAllowed() && !WornRig.backRig(entity).isEmpty());
    }

    /**
     * Sneak + use in the air opens the Rig Settings (no equip). Sneak + use on a block still places the Rig Block ({@link #useOn}).
     * Use equips: an empty chest slot takes it as armor does; a chest plate stays on and the Rig goes to a free Back Slot when
     * Curios and the server config allow; a Rig already worn anywhere refuses (one Rig at a time); otherwise vanilla's armor swap.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            if (WornRig.of(player).worn() || (WornRig.backSlotAllowed() && !WornRig.backRig(player).isEmpty())) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("tentaworks.rig.one_at_a_time").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            ItemStack chest = player.getItemBySlot(SLOT);
            if (!chest.isEmpty() && WornRig.backSlotAllowed()) {
                if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
                if (BackSlot.get().insert(player, stack.copyWithCount(1))) {
                    stack.shrink(1);
                    player.awardStat(Stats.ITEM_USED.get(this));
                    return InteractionResultHolder.success(stack);
                }
            }
            return super.use(level, player, hand);
        }
        if (level.isClientSide) CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> RigSettingsScreen.openInHand(hand));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static class Layered extends TentacleRigItem implements LayeredArmorItem {
        public Layered(Holder<ArmorMaterial> material, Properties properties, ResourceLocation textureLoc,
                       Supplier<BacktankBlockItem> placeable, RigTier tier) {
            super(material, properties, textureLoc, placeable, tier);
        }

        @Override
        public String getArmorTextureLocation(LivingEntity entity, EquipmentSlot slot, ItemStack stack, int layer) {
            return String.format(Locale.ROOT, "%s:textures/models/armor/%s_layer_%d.png", textureLoc.getNamespace(), textureLoc.getPath(), layer);
        }
    }
}
