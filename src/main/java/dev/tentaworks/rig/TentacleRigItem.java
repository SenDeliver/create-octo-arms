package dev.tentaworks.rig;

import com.simibubi.create.content.equipment.armor.BacktankItem;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.foundation.item.LayeredArmorItem;
import dev.tentaworks.client.RigSettingsScreen;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A Backtank fitted with Tentacles (adr/0002): Create's air, diving, Capacity and back rendering apply unchanged.
 */
public class TentacleRigItem extends BacktankItem {
    public static final double MAX_REACH = 16.0;
    private final RigTier tier;

    public TentacleRigItem(ArmorMaterial material, Properties properties, ResourceLocation textureLoc,
                           Supplier<BacktankBlockItem> placeable, RigTier tier) {
        super(material, properties, textureLoc, placeable);
        this.tier = tier;
    }

    public RigTier tier() {
        return tier;
    }

    @Nullable
    public static RigTier wornTier(Player player) {
        return player.getItemBySlot(SLOT).getItem() instanceof TentacleRigItem rig ? rig.tier : null;
    }

    public static boolean isWorn(Player player) {
        return wornTier(player) != null;
    }

    public static double reach(Player player) {
        ItemStack worn = player.getItemBySlot(SLOT);
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

    /** Create's BacktankItem has no durability (and so refuses Mending and Unbreaking); a Rig has durability, so it takes them. */
    @Override
    public boolean canBeDepleted() {
        return true;
    }

    /** Elytra-style: at 1 durability the Rig cannot be Activated (an Active one Sets Down) until repaired. */
    public static boolean wornOut(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /** The Rig never breaks: wear stops at 1 durability, so Air, enchantments and Rig Settings stay with it. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<T> onBroken) {
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tentaworks.stats.air").withStyle(ChatFormatting.GRAY).append(" ")
                .append(Component.translatable("tentaworks.stats.air.value", (int) BacktankUtil.getAir(stack), BacktankUtil.maxAir(stack)).withStyle(ChatFormatting.GOLD)));
        RigStats.addRigLines(stack, tooltip, "");
        if (stack.isDamaged() && !flag.isAdvanced())
            tooltip.add(Component.translatable("item.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage()).withStyle(ChatFormatting.GRAY));
        if (wornOut(stack))
            tooltip.add(Component.translatable("tentaworks.rig.worn_out", tier.repairItem().getDescription()).withStyle(ChatFormatting.RED));
    }

    /** Sneak + use in the air opens the Rig Settings (no equip). Sneak + use on a block still places the Rig Block ({@link #useOn}). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return super.use(level, player, hand);
        if (level.isClientSide) CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> RigSettingsScreen.openInHand(hand));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static class Layered extends TentacleRigItem implements LayeredArmorItem {
        public Layered(ArmorMaterial material, Properties properties, ResourceLocation textureLoc,
                       Supplier<BacktankBlockItem> placeable, RigTier tier) {
            super(material, properties, textureLoc, placeable, tier);
        }

        @Override
        public String getArmorTextureLocation(LivingEntity entity, EquipmentSlot slot, ItemStack stack, int layer) {
            return String.format(Locale.ROOT, "%s:textures/models/armor/%s_layer_%d.png", textureLoc.getNamespace(), textureLoc.getPath(), layer);
        }
    }
}
