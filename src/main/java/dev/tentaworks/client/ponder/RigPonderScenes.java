package dev.tentaworks.client.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import dev.tentaworks.registry.TentaworksItems;
import dev.tentaworks.rig.RigTier;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The "Tentacle Rig" scene: craft, refill under a shaft, Double Jump, climb a wall, hang under a ledge, Set Down.
 * Schematic (tools/gen_ponder.py, exported from a real world): Rig Block (1,1,1) under a creative motor, crafter grid
 * x 3..5 y 1..3 z 6 exiting into a depot (2,1,6), wall x 7 z 0..4 y 1..5 with a ledge y 6 x 4..7 z 2..4.
 */
public final class RigPonderScenes {
    private static final PuppetScript.Face FLOOR = new PuppetScript.Face(new Vec3(0, 1, 0), new Vec3(7, 1, 9), new Vec3(0, 1, 0));
    private static final PuppetScript.Face WALL = new PuppetScript.Face(new Vec3(7, 1, 0), new Vec3(7, 6, 5), new Vec3(-1, 0, 0));
    private static final PuppetScript.Face LEDGE = new PuppetScript.Face(new Vec3(4, 6, 2), new Vec3(7, 6, 5), new Vec3(0, -1, 0));

    private RigPonderScenes() {
    }

    public static void tentacleRig(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("tentacle_rig", "Tentacle Rig");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.8f);
        scene.showBasePlate();
        scene.idle(10);
        craft(scene, util);
        refill(scene, util);
        carry(scene, util);
        scene.markAsFinished();
    }

    private static void craft(CreateSceneBuilder scene, SceneBuildingUtil util) {
        Selection grid = util.select().fromTo(3, 1, 6, 5, 3, 6);
        Selection depot = util.select().position(2, 1, 6);
        scene.world().setKineticSpeed(grid, 0);
        scene.world().showSection(grid.add(depot), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80).text("A Tentacle Rig is built on Mechanical Crafters: a Backtank in the middle, four Tentacles at the corners")
                .pointAt(util.vector().blockSurface(util.grid().at(3, 2, 6), Direction.NORTH)).placeNearTarget().attachKeyFrame();
        scene.idle(20);
        ItemLike[][] rows = {
                {TentaworksItems.TENTACLE.get(), AllItems.PRECISION_MECHANISM.get(), TentaworksItems.TENTACLE.get()},
                {AllBlocks.BRASS_CASING.get(), AllItems.COPPER_BACKTANK.get(), AllBlocks.BRASS_CASING.get()},
                {TentaworksItems.TENTACLE.get(), AllItems.COPPER_SHEET.get(), TentaworksItems.TENTACLE.get()}};
        scene.world().setCraftingResult(util.grid().at(3, 1, 6), TentaworksItems.COPPER_TENTACLE_RIG.asStack());
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++) {
                BlockPos pos = util.grid().at(5 - col, 3 - row, 6);
                ItemStack stack = new ItemStack(rows[row][col]);
                if (row == 1 && col == 1)
                    scene.overlay().showControls(util.vector().blockSurface(pos, Direction.NORTH), Pointing.DOWN, 20).rightClick().withItem(stack);
                scene.world().modifyBlockEntity(pos, MechanicalCrafterBlockEntity.class, be -> be.getInventory().insertItem(0, stack.copy(), false));
                scene.idle(4);
            }
        scene.idle(30);
        scene.world().setKineticSpeed(grid, -48);
        for (int x = 3; x <= 5; x++)
            for (int y = 1; y <= 3; y++)
                if ((x + y) % 2 == 1) scene.world().multiplyKineticSpeed(util.select().position(x, y, 6), -1);
        scene.idle(120);
        scene.overlay().showText(70).text("The Rig lands on the depot. A Netherite Backtank makes a Netherite Rig: longer Reach, half the Air")
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 6))).placeNearTarget().colored(PonderPalette.GREEN).attachKeyFrame();
        scene.idle(80);
        scene.world().hideSection(grid.add(depot), Direction.UP);
        scene.idle(15);
    }

    private static void refill(CreateSceneBuilder scene, SceneBuildingUtil util) {
        BlockPos rig = util.grid().at(1, 1, 1);
        Selection tank = util.select().position(rig);
        Selection motor = util.select().position(1, 2, 1);
        scene.world().setKineticSpeed(tank.add(motor), 0);
        scene.world().showSection(tank, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70).text("Use a Rig on a block to place it. A placed Rig is a Backtank")
                .pointAt(util.vector().blockSurface(rig, Direction.NORTH)).placeNearTarget().attachKeyFrame();
        scene.idle(80);
        scene.world().showSection(motor, Direction.DOWN);
        scene.idle(10);
        scene.world().setKineticSpeed(tank.add(motor), 64);
        scene.overlay().showText(80).text("A turning shaft above refills its Air. This is the only way to refill a Rig")
                .pointAt(util.vector().blockSurface(util.grid().at(1, 2, 1), Direction.NORTH)).placeNearTarget().attachKeyFrame();
        scene.idle(90);
        scene.overlay().showControls(util.vector().topOf(rig), Pointing.DOWN, 40).rightClick();
        scene.idle(10);
        scene.overlay().showText(60).text("Use it with an empty hand to pick it up again, with its Air, enchantments and settings")
                .pointAt(util.vector().blockSurface(rig, Direction.NORTH)).placeNearTarget();
        scene.idle(70);
        scene.world().hideSection(tank.add(motor), Direction.UP);
        scene.idle(15);
    }

    private static void carry(CreateSceneBuilder scene, SceneBuildingUtil util) {
        scene.world().showSection(util.select().fromTo(7, 1, 0, 7, 5, 4).add(util.select().fromTo(4, 6, 2, 7, 6, 4)), Direction.DOWN);
        scene.idle(10);
        Vec3 start = new Vec3(2.5, 1, 1.5);
        RigPuppetElement puppet = new RigPuppetElement(RigTier.COPPER, start, -90);
        PuppetScript rig = new PuppetScript(scene, puppet, start, -90, 3.2);
        rig.show(15);
        rig.idle(20);
        scene.overlay().showText(70).text("Worn in the chest slot, the Rig carries you. Double Jump to activate it")
                .pointAt(start.add(0, 1.8, 0)).placeNearTarget().attachKeyFrame();
        rig.idle(40);
        rig.activate(1.5, List.of(FLOOR));
        rig.idle(30);
        scene.overlay().showText(70).text("Tentacles grip floors, walls and ceilings. You move where you look, Space goes up, Sneak goes down")
                .pointAt(new Vec3(3.5, 2.5, 1.5)).placeNearTarget().attachKeyFrame();
        rig.move(new Vec3(5.4, 2.5, 1.5), 36, List.of(FLOOR, WALL));
        rig.idle(10);
        rig.move(new Vec3(5.6, 4.0, 1.5), 30, List.of(FLOOR, WALL));
        rig.idle(20);
        scene.overlay().showText(70).text("Look up at a wall to climb it. The hands stay free the whole time")
                .pointAt(new Vec3(7, 4.5, 1.0)).placeNearTarget().attachKeyFrame();
        rig.idle(30);
        rig.move(new Vec3(5.6, 3.8, 2.4), 20, List.of(WALL, LEDGE));
        rig.move(new Vec3(4.9, 3.5, 2.4), 16, List.of(WALL, LEDGE));
        rig.idle(10);
        scene.overlay().showText(80).text("Under a ledge the Rig simply hangs on. Holding still, moving and climbing all use the Backtank's Air")
                .pointAt(new Vec3(5.5, 6, 2.4)).placeNearTarget().attachKeyFrame();
        rig.idle(90);
        scene.overlay().showText(90).text("Double Jump again to be Set Down: the Rig lowers you to the floor and Stows. With no floor in Reach, it lets go")
                .pointAt(new Vec3(4.9, 4.5, 2.4)).placeNearTarget().attachKeyFrame();
        rig.idle(40);
        rig.move(new Vec3(4.9, 1.0, 2.4), 40, List.of(WALL, FLOOR));
        rig.idle(6);
        rig.stow();
        rig.idle(70);
        scene.overlay().showText(70).text("Out of Air, the Rig only Crawls slowly down to the nearest floor. Refill it in time")
                .pointAt(new Vec3(4.9, 2.0, 2.4)).placeNearTarget().colored(PonderPalette.RED);
        rig.idle(80);
    }
}
