package dev.tentaworks.display;

import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.PercentOrProgressBarDisplaySource;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import dev.tentaworks.rig.RigBlock;
import dev.tentaworks.rig.RigStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** Display Link source "Rig Air" on a placed Rig Block. Mode 0: Air amount, 1: percent, 2: progress bar. */
public class RigAirDisplaySource extends PercentOrProgressBarDisplaySource {
    private static final String KEY = "tentaworks.display_source.rig_air.";

    private static BacktankBlockEntity rig(DisplayLinkContext context) {
        return context.getSourceBlockEntity() instanceof BacktankBlockEntity be && be.getBlockState().getBlock() instanceof RigBlock ? be : null;
    }

    @Override
    protected Float getProgress(DisplayLinkContext context) {
        BacktankBlockEntity be = rig(context);
        if (be == null) return null;
        int max = RigStats.maxAir(be);
        return max <= 0 ? 0f : (float) be.getAirLevel() / max;
    }

    @Override
    protected MutableComponent formatNumeric(DisplayLinkContext context, Float level) {
        BacktankBlockEntity be = rig(context);
        if (context.sourceConfig().getInt("Mode") == 0 && be != null) return Component.literal(String.valueOf(be.getAirLevel()));
        return super.formatNumeric(context, level);
    }

    @Override
    protected boolean progressBarActive(DisplayLinkContext context) {
        return context.sourceConfig().getInt("Mode") == 2;
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
        super.initConfigurationWidgets(context, builder, isFirstLine);
        if (isFirstLine) return;
        builder.addSelectionScrollInput(0, 120, (si, l) -> si
                .forOptions(List.of(Component.translatable(KEY + "air"), Component.translatable(KEY + "percent"), Component.translatable(KEY + "progress_bar")))
                .titled(Component.translatable(KEY + "display")), "Mode");
    }
}
