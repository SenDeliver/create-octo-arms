package dev.tentaworks.rig;

import dev.tentaworks.config.TentaworksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Dual Wield (CONTEXT: Actuators): with a fitted Rig whose Actuators may act, no heavy hold and the off-hand Actuator not
 * holding, the off-hand item works along with the main-hand one ({@code actuators.dualWield}). Mining: the break speed is the
 * sum of both tools that are effective on the block ({@link #speedFactor}, each with its own Efficiency); an off-hand tool alone
 * does the work and passes the harvest check, the loot still comes from the main hand. Melee: the off-hand item's damage is
 * added to the hit ({@link #bonus}), scaled by the attack strength as vanilla scales the main hand's. Each bonus action wears the
 * off-hand item and pays {@code air.dualWield}. Use gets no speed-up. The same rule on client and server: the break speed must
 * agree on both, so the client reads the hold from {@link #clientSide}.
 */
public final class DualWield {
    private static Function<Player, HoldArm> clientArm = p -> HoldArm.NONE;
    private static BiConsumer<ServerPlayer, String> listener = (p, kind) -> {
    };

    private DualWield() {
    }

    public static void clientSide(Function<Player, HoldArm> arm) {
        clientArm = arm;
    }

    /** Dev hook: told of each bonus action ("mine", "hit"). */
    public static void listen(BiConsumer<ServerPlayer, String> l) {
        listener = l;
    }

    public static HoldArm arm(Player player) {
        if (player instanceof ServerPlayer sp) return Grab.arm(sp);
        return player.level().isClientSide() ? clientArm.apply(player) : HoldArm.NONE;
    }

    /** Dual Wield may work: the Server Config allows it, the Actuators are fitted and ready, the off-hand one free, an item in the off hand. */
    public static boolean allows(boolean enabled, boolean fitted, boolean ready, HoldArm arm, boolean offEmpty) {
        return enabled && fitted && ready && !arm.uses(InteractionHand.OFF_HAND) && !offEmpty;
    }

    public static boolean on(Player player) {
        return allows(TentaworksConfig.value(TentaworksConfig.DUAL_WIELD), Actuators.fitted(player), Actuators.ready(player), arm(player),
                player.getOffhandItem().isEmpty());
    }

    /** A tool of this base speed on a block is effective there: it beats a bare hand (vanilla adds Efficiency only then). */
    public static boolean effective(double base) {
        return base > 1.0;
    }

    /**
     * What the break speed is multiplied by: vanilla's speed counts the main tool ({@code mainBase} + its Efficiency
     * {@code mainEff} when effective); with both effective it becomes their sum, with only the off-hand one effective that one
     * alone. 1 when the off-hand tool does nothing here.
     */
    public static double speedFactor(double mainBase, double mainEff, double offBase, double offEff) {
        if (!effective(offBase) || mainBase <= 0) return 1.0;
        double off = offBase + offEff;
        if (!effective(mainBase)) return off / mainBase;
        double main = mainBase + mainEff;
        return main <= 0 ? 1.0 : (main + off) / main;
    }

    /** The off-hand item's melee bonus: its attack damage bonus {@code item} and its enchantments' {@code enchant}, at attack strength {@code scale}. */
    public static float bonus(double item, double enchant, float scale) {
        return (float) (item * (0.2F + scale * scale * 0.8F) + enchant * scale);
    }

    /** Efficiency (mining efficiency the stack would add held in the main hand). */
    static double efficiency(ItemStack stack) {
        double[] sum = {0};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.is(Attributes.MINING_EFFICIENCY) && mod.operation() == AttributeModifier.Operation.ADD_VALUE) sum[0] += mod.amount();
        });
        return sum[0];
    }

    /** The attack damage the stack adds held in the main hand. */
    static double attackBonus(ItemStack stack) {
        double[] sum = {0};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.is(Attributes.ATTACK_DAMAGE) && mod.operation() == AttributeModifier.Operation.ADD_VALUE) sum[0] += mod.amount();
        });
        return sum[0];
    }

    /** The break speed factor for this player on {@code state} ({@link #speedFactor}), 1 when Dual Wield is off. */
    public static double speedFactor(Player player, BlockState state) {
        if (!on(player)) return 1.0;
        ItemStack off = player.getOffhandItem();
        return speedFactor(player.getMainHandItem().getDestroySpeed(state), player.getAttributeValue(Attributes.MINING_EFFICIENCY),
                off.getDestroySpeed(state), efficiency(off));
    }

    /** The off-hand tool is what makes this block harvestable. */
    public static boolean harvests(Player player, BlockState state) {
        ItemStack off = player.getOffhandItem();
        return on(player) && effective(off.getDestroySpeed(state)) && off.isCorrectToolForDrops(state);
    }

    /**
     * A block broken in survival: the off-hand tool, if it worked on it, wears as {@code mineBlock} wears the main one, and
     * Dual Wield pays its Air.
     */
    public static void mined(ServerPlayer player, Level level, BlockState state, BlockPos pos) {
        if (!on(player)) return;
        ItemStack off = player.getOffhandItem();
        if (!effective(off.getDestroySpeed(state))) return;
        Tool tool = off.get(DataComponents.TOOL);
        if (tool != null && state.getDestroySpeed(level, pos) != 0.0F && tool.damagePerBlock() > 0) off.hurtAndBreak(tool.damagePerBlock(), player, EquipmentSlot.OFFHAND);
        pay(player, "mine");
    }

    /** The melee bonus for this hit on {@code target}, 0 when Dual Wield adds nothing. */
    public static float hitBonus(ServerPlayer player, Entity target, DamageSource source) {
        if (!(target instanceof LivingEntity) || !on(player)) return 0;
        if (target instanceof Player && !TentaworksConfig.value(TentaworksConfig.FAR_USE_PLAYERS)) return 0;
        ItemStack off = player.getOffhandItem();
        double item = Math.max(0, attackBonus(off));
        double enchant = player.level() instanceof ServerLevel sl ? Math.max(0, EnchantmentHelper.modifyDamage(sl, off, target, source, 0.0F)) : 0;
        if (item <= 0 && enchant <= 0) return 0;
        return bonus(item, enchant, player.getAttackStrengthScale(0.5F));
    }

    /** A hit the off-hand item helped land: it wears as a weapon does when it hits, and Dual Wield pays its Air. */
    public static void hit(ServerPlayer player) {
        ItemStack off = player.getOffhandItem();
        int wear = off.getItem() instanceof DiggerItem ? 2 : off.getItem() instanceof SwordItem || off.getItem() instanceof TridentItem || off.getItem() instanceof MaceItem ? 1 : 0;
        if (wear > 0) off.hurtAndBreak(wear, player, EquipmentSlot.OFFHAND);
        pay(player, "hit");
    }

    private static void pay(ServerPlayer player, String kind) {
        double air = RigAir.actuatorCost(RigAir.worn(player), TentaworksConfig.value(TentaworksConfig.AIR_DUAL_WIELD));
        RigAir.pay(player, air);
        listener.accept(player, kind);
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.BreakSpeed e) -> {
            double f = speedFactor(e.getEntity(), e.getState());
            if (f != 1.0) e.setNewSpeed((float) (e.getNewSpeed() * f));
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.HarvestCheck e) -> {
            if (!e.canHarvest() && e.getTargetBlock().requiresCorrectToolForDrops() && harvests(e.getEntity(), e.getTargetBlock())) e.setCanHarvest(true);
        });
    }
}
