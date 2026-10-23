package dev.tentaworks.rig;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import dev.tentaworks.registry.TentaworksBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Rig Blocks use Create's Backtank block entity. Its type must list them as valid blocks (rebuilt in common setup through
 * the access transformer), or the chunk never ticks the block entity and the renderer skips it. Runs in the dev server (fmlTest).
 */
class BacktankTypeTest {
    private static Block[] rigBlocks() {
        return new Block[]{TentaworksBlocks.COPPER_TENTACLE_RIG.get(), TentaworksBlocks.NETHERITE_TENTACLE_RIG.get()};
    }

    @Test
    void rigBlocksAreValid() {
        BlockEntityType<?> type = AllBlockEntityTypes.BACKTANK.get();
        for (Block block : rigBlocks())
            for (BlockState state : block.getStateDefinition().getPossibleStates())
                assertTrue(type.isValid(state), state + " not valid for Create's backtank block entity");
    }

    @Test
    void createBacktanksStayValid() {
        BlockEntityType<?> type = AllBlockEntityTypes.BACKTANK.get();
        assertTrue(type.isValid(AllBlocks.COPPER_BACKTANK.getDefaultState()));
        assertTrue(type.isValid(AllBlocks.NETHERITE_BACKTANK.getDefaultState()));
    }

    @Test
    void rigBlockMakesABacktankBlockEntity() {
        for (Block block : rigBlocks()) {
            RigBlock rig = (RigBlock) block;
            BlockEntity be = rig.newBlockEntity(BlockPos.ZERO, rig.defaultBlockState());
            assertNotNull(be, block + " made no block entity");
            assertSame(AllBlockEntityTypes.BACKTANK.get(), be.getType());
            assertTrue(be.getType().isValid(be.getBlockState()));
        }
    }
}
