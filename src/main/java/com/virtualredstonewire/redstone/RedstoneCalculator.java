package com.virtualredstonewire.redstone;

import com.virtualredstonewire.block.VirtualRedstoneSourceBlock;
import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class RedstoneCalculator
{
    private static final Map<BlockPos, Integer> LAST_SIGNAL = new HashMap<>();
    private static final Set<BlockPos> DIRTY_INPUTS = new HashSet<>();

    public static void markDirtyInput(BlockPos inputPos)
    {
        DIRTY_INPUTS.add(inputPos.immutable());
    }

    public static void tick(ServerLevel level)
    {
        if (DIRTY_INPUTS.isEmpty()) return;

        CableNetwork network = CableNetworkManager.get(level);
        Set<BlockPos> toProcess = new HashSet<>(DIRTY_INPUTS);
        DIRTY_INPUTS.clear();

        for (BlockPos inputPos : toProcess)
        {
            int currentPower = getSignalFrom(inputPos, level);
            Integer lastPower = LAST_SIGNAL.get(inputPos);
            if (lastPower != null && lastPower == currentPower)
            {
                continue;
            }
            LAST_SIGNAL.put(inputPos, currentPower);

            Set<Map.Entry<BlockPos, Direction>> outputs = network.getOutputsForInput(inputPos);
            for (Map.Entry<BlockPos, Direction> edge : outputs)
            {
                BlockPos toPos = edge.getKey();
                Direction toFace = edge.getValue();

                Set<BlockPos> inputs = network.getInputsForOutput(toPos, toFace);
                int maxPower = 0;
                for (BlockPos inPos : inputs)
                {
                    int p = getSignalFrom(inPos, level);
                    if (p > maxPower) maxPower = p;
                }

                BlockPos sourcePos = toPos.relative(toFace);
                BlockState state = level.getBlockState(sourcePos);
                if (state.getBlock() instanceof VirtualRedstoneSourceBlock)
                {
                    int oldPower = state.getValue(VirtualRedstoneSourceBlock.POWER);
                    if (oldPower != maxPower)
                    {
                        level.setBlock(sourcePos,
                            state.setValue(VirtualRedstoneSourceBlock.POWER, maxPower), 2);
                    }
                }
            }
        }
    }

    private static int getSignalFrom(BlockPos pos, ServerLevel level)
    {
        int max = 0;
        for (Direction dir : Direction.values())
        {
            int signal = level.getSignal(pos, dir);
            if (signal > max) max = signal;
        }
        return max;
    }
}
