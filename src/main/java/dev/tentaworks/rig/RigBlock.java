package dev.tentaworks.rig;

import com.simibubi.create.content.equipment.armor.BacktankBlock;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A placed Rig: a Create backtank block (same block entity type), so a shaft above refills its Air.
 * {@link #ARMS}: placed Rigs show their folded Tentacles; the default state (no arms) is what Create's worn Backtank layer
 * draws on the back, where TentacleLayer draws the Tentacles itself.
 */
public class RigBlock extends BacktankBlock {
    public static final BooleanProperty ARMS = BooleanProperty.create("arms");
    private final RigTier tier;

    public RigBlock(Properties properties, RigTier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(ARMS, false));
    }

    public RigTier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ARMS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(ARMS, true);
    }
}
