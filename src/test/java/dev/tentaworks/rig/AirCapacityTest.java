package dev.tentaworks.rig;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.tentaworks.registry.TentaworksItems;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** A Rig holds air.capacityFactor (2 by default) times a Backtank's Air; a Backtank keeps Create's own. */
class AirCapacityTest {
    @Test
    void rigStacksOnly() {
        assertEquals(2.0, RigAir.capacityFactor());
        assertEquals(1800, RigAir.maxAir(TentaworksItems.COPPER_TENTACLE_RIG.asStack(), 900));
        assertEquals(1800, RigAir.maxAir(TentaworksItems.NETHERITE_TENTACLE_RIG.asStack(), 900));
        assertEquals(900, RigAir.maxAir(AllItems.COPPER_BACKTANK.asStack(), 900));
        assertEquals(900, RigAir.maxAir(AllItems.NETHERITE_BACKTANK.asStack(), 900));
    }

    /** Both mixin targets load with the Air capacity handler merged in (a target that moved fails here, not in game). */
    @Test
    void mixinsApplied() throws ClassNotFoundException {
        ClassLoader loader = getClass().getClassLoader();
        for (String name : new String[]{"com.simibubi.create.content.equipment.armor.BacktankUtil", "com.simibubi.create.content.equipment.armor.BacktankBlockEntity"}) {
            Class<?> c = Class.forName(name, false, loader);
            assertTrue(Arrays.stream(c.getDeclaredMethods()).anyMatch(m -> m.getName().contains("rigCapacity")), name);
        }
    }

    /** Through Create's own method, when its server config is readable in the test run. */
    @Test
    void createMaxAir() {
        ItemStack rig = TentaworksItems.COPPER_TENTACLE_RIG.asStack();
        ItemStack tank = AllItems.COPPER_BACKTANK.asStack();
        int base;
        try {
            base = BacktankUtil.maxAir(tank);
        } catch (RuntimeException e) {
            base = -1;
        }
        assumeTrue(base > 0, "Create server config not loaded");
        assertEquals(RigAir.capacity(base), BacktankUtil.maxAir(rig));
    }
}
