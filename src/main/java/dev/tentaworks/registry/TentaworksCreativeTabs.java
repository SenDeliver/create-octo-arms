package dev.tentaworks.registry;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import dev.tentaworks.TentaworksIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TentaworksCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TentaworksIds.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = REGISTER.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tentaworks"))
            .icon(TentaworksItems.COPPER_TENTACLE_RIG::asStack)
            .displayItems((params, out) -> {
                out.accept(TentaworksItems.TENTACLE.get());
                for (Item rig : new Item[]{TentaworksItems.COPPER_TENTACLE_RIG.get(), TentaworksItems.NETHERITE_TENTACLE_RIG.get()}) {
                    out.accept(rig);
                    int air = fullAir();
                    if (air > 0) {
                        ItemStack filled = new ItemStack(rig);
                        filled.set(AllDataComponents.BACKTANK_AIR, air);
                        out.accept(filled);
                    }
                }
            })
            .build());

    private TentaworksCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        REGISTER.register(modBus);
    }

    private static int fullAir() {
        try {
            return BacktankUtil.maxAirWithoutEnchants();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
