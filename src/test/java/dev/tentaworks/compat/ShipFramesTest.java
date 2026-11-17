package dev.tentaworks.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipFramesTest {
    @Test
    void withoutSableNoSableClassIsNeeded() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("dev.ryanhcode.sable.companion.SableCompanion"));
        assertSame(NoShips.INSTANCE, ShipFrames.get());
    }

    @Test
    void aHitIsMappedByTheBlockItsLocationIsGivenIn() {
        BlockPos pos = new BlockPos(20481032, 129, 20491273);
        Vec3 at = new Vec3(20481032, 129.5, 20491273.5);
        assertEquals(pos, ShipFrames.frameBlock(new BlockHitResult(at, Direction.WEST, pos, false)));
        assertNull(ShipFrames.frameBlock(BlockHitResult.miss(at, Direction.WEST, pos)));
        assertEquals(new BlockPos(20481033, 129, 20491271), ShipFrames.frameBlock(new EntityHitResult(null, new Vec3(20481033.5, 129.5, 20491271.97))));
    }

    @Test
    void withoutSableAHitIsAlreadyInTheWorld() {
        Vec3 at = new Vec3(3.5, 64, -2.25);
        assertEquals(at, ShipFrames.get().worldAt(null, new BlockHitResult(at, Direction.UP, BlockPos.containing(at), false)));
        assertEquals(at, ShipFrames.get().worldAt(null, new EntityHitResult(null, at)));
        assertNull(ShipFrames.get().owner(null, BlockPos.containing(at)));
    }
}
