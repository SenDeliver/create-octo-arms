package dev.tentaworks.rig;

import com.simibubi.create.content.equipment.armor.BacktankBlockEntity;
import dev.tentaworks.TentaworksIds;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Engineer's Goggles text of a placed Rig Block, Create style, above Create's kinetic stats. */
public final class RigGoggles {
    private RigGoggles() {
    }

    private static LangBuilder lang() {
        return new LangBuilder(TentaworksIds.MOD_ID);
    }

    public static boolean add(BacktankBlockEntity be, List<Component> tooltip, boolean kineticAdded) {
        if (!(be.getBlockState().getBlock() instanceof RigBlock)) return false;
        List<Component> out = new ArrayList<>();
        int air = be.getAirLevel(), max = RigStats.maxAir(be);
        lang().translate("goggles.rig_block").forGoggles(out);
        lang().translate("stats.air").style(ChatFormatting.GRAY).forGoggles(out);
        lang().text(air + " / " + max).style(ChatFormatting.AQUA).space()
                .add(lang().translate("goggles.percent", max == 0 ? 0 : Math.round(100f * air / max)).style(ChatFormatting.DARK_GRAY))
                .forGoggles(out, 1);
        double rate = RigStats.fillPerSecond(be);
        if (air >= max) lang().translate("goggles.full").style(ChatFormatting.GREEN).forGoggles(out, 1);
        else if (rate > 0) lang().translate("goggles.filling", RigStats.num(rate)).style(ChatFormatting.GOLD).forGoggles(out, 1);
        else lang().translate("goggles.no_shaft").style(ChatFormatting.DARK_GRAY).forGoggles(out, 1);
        List<Component> rig = new ArrayList<>();
        RigStats.addRigLines(RigStats.stackOf(be), rig, "");
        for (Component line : rig) lang().add(line.copy()).forGoggles(out);
        tooltip.addAll(0, out);
        return true;
    }
}
