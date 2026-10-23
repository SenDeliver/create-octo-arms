package dev.tentaworks.rig;

import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import dev.tentaworks.mixin.BacktankBlockEntityAccessor;
import dev.tentaworks.registry.TentaworksItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import java.util.Locale;

/** Rig numbers for tooltips, goggles and Display Links: Air, refill rate, Reach, top Speed, Air use. */
public final class RigStats {
    private RigStats() {
    }

    /** The Rig item a placed Rig Block stands for, with its saved tag (enchantments, settings), as Create's pick-up builds it. */
    public static ItemStack stackOf(BacktankBlockEntity be) {
        if (!(be.getBlockState().getBlock() instanceof RigBlock rig)) return ItemStack.EMPTY;
        Item item = rig.tier() == RigTier.NETHERITE ? TentaworksItems.NETHERITE_TENTACLE_RIG.get() : TentaworksItems.COPPER_TENTACLE_RIG.get();
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = be.getVanillaTag().copy();
        tag.putInt("Air", be.getAirLevel());
        stack.setTag(tag);
        return stack;
    }

    public static int maxAir(BacktankBlockEntity be) {
        return BacktankUtil.maxAir(((BacktankBlockEntityAccessor) be).tentaworks$capacityLevel());
    }

    /** Air per second a placed backtank gains (Create's refill: +clamp((rpm-100)/20,1,5) every clamp(20-rpm/5,0,20)+1 ticks); 0 when it cannot fill. */
    public static double fillPerSecond(BacktankBlockEntity be) {
        float rpm = Math.abs(be.getSpeed());
        if (rpm == 0) return 0;
        BlockState state = be.getBlockState();
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) return 0;
        int increment = Mth.clamp(((int) rpm - 100) / 20, 1, 5);
        int period = Mth.clamp((int) (128.0F - rpm / 5.0F) - 108, 0, 20) + 1;
        return increment * 20.0 / period;
    }

    /** Air use relative to a plain Copper Rig (tier factor and Thrift). */
    public static double airUse(ItemStack rig) {
        RigTier tier = rig.getItem() instanceof TentacleRigItem item ? item.tier() : RigTier.COPPER;
        return tier.airFactor() * RigEnchants.thrift(rig);
    }

    public static String num(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** Reach, top Speed and (when not 100 %) Air use of a Rig stack. */
    public static void addRigLines(ItemStack rig, List<Component> out, String indent) {
        if (!(rig.getItem() instanceof TentacleRigItem item)) return;
        out.add(line(indent, "reach", num(item.reach(rig))));
        out.add(line(indent, "top_speed", num(TentacleRigItem.topSpeed(rig))));
        int use = (int) Math.round(airUse(rig) * 100);
        if (use != 100) out.add(line(indent, "air_use", use));
    }

    private static MutableComponent line(String indent, String key, Object value) {
        return Component.literal(indent).append(Component.translatable("tentaworks.stats." + key).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" ")).append(Component.translatable("tentaworks.stats." + key + ".value", value).withStyle(ChatFormatting.GOLD));
    }
}
