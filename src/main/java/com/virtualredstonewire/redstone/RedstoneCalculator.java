package com.virtualredstonewire.redstone;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

public class RedstoneCalculator
{
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
            Set<Map.Entry<BlockPos, Direction>> outputs = network.getOutputsForInput(inputPos);
            for (Map.Entry<BlockPos, Direction> edge : outputs)
            {
                level.updateNeighborsAt(edge.getKey(), level.getBlockState(edge.getKey()).getBlock());
            }
        }
    }
}
