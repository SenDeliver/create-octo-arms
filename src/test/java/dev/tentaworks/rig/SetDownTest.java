package dev.tentaworks.rig;

import dev.tentaworks.rig.GripFaceTest.World;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Set Down at the looked spot: where the held box goes for each face, and when a spot is valid. */
class SetDownTest {
    private static final double W = 0.9, H = 0.9;

    private static Predicate<AABB> free(World w) {
        return b -> {
            for (int x = Mth.floor(b.minX); x <= Mth.floor(b.maxX - 1.0E-7); x++)
                for (int y = Mth.floor(b.minY); y <= Mth.floor(b.maxY - 1.0E-7); y++)
                    for (int z = Mth.floor(b.minZ); z <= Mth.floor(b.maxZ - 1.0E-7); z++)
                        if (!w.getBlockState(new BlockPos(x, y, z)).isAir()) return false;
            return true;
        };
    }

    private static World floor(int y) {
        World w = new World();
        for (int x = -4; x <= 8; x++)
            for (int z = -4; z <= 4; z++) w.put(x, y, z, Blocks.STONE.defaultBlockState());
        return w;
    }

    @Test
    void onATopFaceItStandsWhereTheCrosshairHits() {
        Vec3 hit = new Vec3(2.3, 1.0, 0.4);
        assertEquals(hit, SetDown.feet(hit, Direction.UP, W, H, free(floor(0))));
    }

    @Test
    void aBottomFaceGivesNoSpot() {
        assertNull(SetDown.feet(new Vec3(2.3, 3.0, 0.4), Direction.DOWN, W, H, b -> true));
    }

    @Test
    void besideASideFaceItDropsToTheFloorBelow() {
        World w = floor(0).put(3, 1, 0, Blocks.STONE.defaultBlockState()).put(3, 2, 0, Blocks.STONE.defaultBlockState()).put(3, 3, 0, Blocks.STONE.defaultBlockState());
        Vec3 feet = SetDown.feet(new Vec3(3.0, 3.5, 0.5), Direction.WEST, W, H, free(w));
        assertNotNull(feet);
        assertEquals(3.0 - W / 2, feet.x, 0.02, "clear of the wall");
        assertEquals(1.0, feet.y, 0.01, "on the floor 2 blocks down");
        assertTrue(free(w).test(SetDown.box(feet.x, feet.y, feet.z, W, H)));
    }

    @Test
    void noFloorWithinThreeBlocksGivesNoSpot() {
        World w = floor(-4).put(3, 3, 0, Blocks.STONE.defaultBlockState());
        assertNull(SetDown.feet(new Vec3(3.0, 3.5, 0.5), Direction.WEST, W, H, free(w)));
    }

    @Test
    void aSpotIsValidWhenItFitsKeepsClearOfTheWearerAndIsWithinReach() {
        World w = floor(0);
        Vec3 body = new Vec3(0.5, 2.4, 0.5);
        AABB wearer = AABB.ofSize(body, 0.6, 1.8, 0.6);
        AABB ok = SetDown.box(3.5, 1.0, 0.5, W, H);
        assertTrue(SetDown.ok(ok, wearer, body, 5.0, free(w)));
        assertFalse(SetDown.ok(SetDown.box(0.7, 1.5, 0.5, W, H), wearer, body, 5.0, free(w)), "inside the wearer");
        assertFalse(SetDown.ok(SetDown.box(7.5, 1.0, 0.5, W, H), wearer, body, 5.0, free(w)), "past Reach of the body");
        assertFalse(SetDown.ok(SetDown.box(3.5, 0.5, 0.5, W, H), wearer, body, 5.0, free(w)), "in the floor");
        assertFalse(SetDown.ok(new AABB(Double.NaN, 1, 0, 1, 2, 1), wearer, body, 5.0, b -> true), "not finite");
    }

    @Test
    void aSpotMustBeInSightOfTheBody() {
        World w = floor(0);
        Vec3 body = new Vec3(0.5, 2.4, 0.5);
        AABB box = SetDown.box(3.5, 1.0, 0.5, W, H);
        assertTrue(SetDown.sight(w, body, box, CollisionContext.empty()));
        for (int y = 1; y <= 4; y++) w.put(2, y, 0, Blocks.STONE.defaultBlockState());
        assertFalse(SetDown.sight(w, body, box, CollisionContext.empty()), "through a wall");
        assertTrue(SetDown.sight(w, body, AABB.ofSize(body, 1, 1, 1), CollisionContext.empty()), "the body inside the box");
    }
}
