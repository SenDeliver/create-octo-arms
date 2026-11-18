package dev.tentaworks.rig;

import com.simibubi.create.AllItems;
import dev.tentaworks.compat.BetterCombat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Items an Actuator never carries stay in the own hand; a Better Combat weapon is one, and it keeps its own entity range. */
class OwnHandTest {
    @Test
    void createAndVanillaItemsDrawnInTheHand() {
        assertTrue(OwnHand.keeps(new ItemStack(Items.FILLED_MAP)));
        assertTrue(OwnHand.keeps(AllItems.POTATO_CANNON.asStack()));
        assertTrue(OwnHand.keeps(AllItems.WORLDSHAPER.asStack()));
        assertFalse(OwnHand.keeps(new ItemStack(Items.IRON_SWORD)));
        assertFalse(OwnHand.keeps(new ItemStack(Items.DIAMOND_PICKAXE)));
        assertFalse(OwnHand.keeps(ItemStack.EMPTY));
    }

    @Test
    void aBetterCombatWeaponStaysInTheOwnHand() {
        assertTrue(OwnHand.keeps(new ItemStack(Items.IRON_SWORD), true));
        assertFalse(OwnHand.keeps(new ItemStack(Items.IRON_SWORD), false));
    }

    @Test
    void withoutBetterCombatNothingIsItsWeapon() {
        assertFalse(BetterCombat.weapon(new ItemStack(Items.IRON_SWORD)));
        assertFalse(BetterCombat.weapon(ItemStack.EMPTY));
    }

    @Test
    void betterCombatOwnsTheEntityRange() {
        assertTrue(FarUse.liftsEntities(true, false));
        assertFalse(FarUse.liftsEntities(true, true));
        assertFalse(FarUse.liftsEntities(false, false));
    }
}
