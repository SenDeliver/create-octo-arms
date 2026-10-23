package dev.tentaworks.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import net.createmod.catnip.math.Pointing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ponder schematics, built in a real world: schematic NAME FROM TO (StructureTemplate, air skipped, written to
 * -Ptentaworks.ponderOut), pondergrid X Y Z (3x3 Mechanical Crafters facing north, origin bottom-left: columns flow
 * down, the bottom row flows west and exits west of the origin).
 */
final class DevPonder {
    private static final Logger LOG = LoggerFactory.getLogger("tentaworks-dev");

    private DevPonder() {
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tentaworks").requires(s -> s.hasPermission(2))
                .then(Commands.literal("schematic").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos())
                                .executes(DevPonder::schematic)))))
                .then(Commands.literal("pondergrid").then(Commands.argument("at", BlockPosArgument.blockPos()).executes(DevPonder::grid))));
    }

    private static BlockState crafter(Direction pointTo) {
        BlockState base = AllBlocks.MECHANICAL_CRAFTER.getDefaultState().setValue(MechanicalCrafterBlock.HORIZONTAL_FACING, Direction.NORTH);
        for (Pointing p : Pointing.values()) {
            BlockState s = base.setValue(MechanicalCrafterBlock.POINTING, p);
            if (MechanicalCrafterBlock.getTargetDirection(s) == pointTo) return s;
        }
        throw new IllegalStateException("no pointing for " + pointTo);
    }

    private static int grid(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos o = BlockPosArgument.getLoadedBlockPos(ctx, "at");
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                level.setBlock(o.offset(x, y, 0), crafter(y > 0 ? Direction.DOWN : Direction.WEST), 3);
        LOG.info("PONDERGRID {}", o.toShortString());
        ctx.getSource().sendSuccess(() -> Component.literal("pondergrid"), false);
        return 1;
    }

    private static int schematic(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        String name = StringArgumentType.getString(ctx, "name");
        BlockPos a = BlockPosArgument.getLoadedBlockPos(ctx, "from"), b = BlockPosArgument.getLoadedBlockPos(ctx, "to");
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(level, min, max.subtract(min).offset(1, 1, 1), false, Blocks.AIR);
        CompoundTag tag = t.save(new CompoundTag());
        String dir = System.getProperty("tentaworks.ponderOut");
        try {
            Path out = Path.of(dir == null ? "ponder-export" : dir).resolve(name + ".nbt");
            Files.createDirectories(out.getParent());
            NbtIo.writeCompressed(tag, out.toFile());
            LOG.info("SCHEMATIC {} -> {}", name, out.toAbsolutePath());
        } catch (Exception e) {
            LOG.error("SCHEMATIC {} failed", name, e);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("schematic " + name), false);
        return 1;
    }
}
