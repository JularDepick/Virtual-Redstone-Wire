package com.virtualredstonewire.redstone;

import com.virtualredstonewire.block.VirtualRedstoneSourceBlock;
import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class RedstoneCalculator
{
    private static final Set<Map.Entry<BlockPos, Direction>> DIRTY_OUTPUTS = new HashSet<>();

    public static void markDirty(BlockPos toPos, Direction toFace)
    {
        DIRTY_OUTPUTS.add(Map.entry(toPos.immutable(), toFace));
    }

    public static void tick(ServerLevel level)
    {
        if (DIRTY_OUTPUTS.isEmpty()) return;

        CableNetwork network = CableNetworkManager.get(level);
        Set<Map.Entry<BlockPos, Direction>> toProcess = new HashSet<>(DIRTY_OUTPUTS);
        DIRTY_OUTPUTS.clear();

        for (Map.Entry<BlockPos, Direction> output : toProcess)
        {
            BlockPos toPos = output.getKey();
            Direction toFace = output.getValue();

            Set<BlockPos> inputs = network.getInputsForOutput(toPos, toFace);
            if (inputs.isEmpty()) continue;

            int maxPower = 0;
            for (BlockPos inputPos : inputs)
            {
                int power = getInputPower(level, inputPos);
                if (power > maxPower) maxPower = power;
            }

            BlockPos sourcePos = toPos.relative(toFace.getOpposite());
            BlockState state = level.getBlockState(sourcePos);
            if (state.getBlock() instanceof VirtualRedstoneSourceBlock)
            {
                int currentPower = state.getValue(VirtualRedstoneSourceBlock.POWER);
                if (currentPower != maxPower)
                {
                    level.setBlock(sourcePos,
                        state.setValue(VirtualRedstoneSourceBlock.POWER, maxPower),
                        3);
                    level.updateNeighborsAt(sourcePos, state.getBlock());
                }
            }
        }
    }

    private static int getInputPower(Level level, BlockPos pos)
    {
        int maxPower = 0;
        BlockState state = level.getBlockState(pos);

        if (state.isRedstoneConductor(level, pos))
        {
            for (Direction dir : Direction.values())
            {
                int power = level.getSignal(pos, dir);
                if (power > maxPower) maxPower = power;
            }
        }
        else
        {
            for (Direction dir : Direction.values())
            {
                BlockPos neighborPos = pos.relative(dir);
                BlockState neighborState = level.getBlockState(neighborPos);
                int power = neighborState.getSignal(level, neighborPos, dir.getOpposite());
                if (power > maxPower) maxPower = power;
            }
        }

        return maxPower;
    }
}
