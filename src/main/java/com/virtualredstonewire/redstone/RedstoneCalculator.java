package com.virtualredstonewire.redstone;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;

public class RedstoneCalculator
{
    private static final Map<ResourceKey<Level>, Set<BlockPos>> DIRTY_INPUTS = new HashMap<>();

    public static void markDirtyInput(ServerLevel level, BlockPos inputPos)
    {
        DIRTY_INPUTS.computeIfAbsent(level.dimension(), k -> new HashSet<>())
            .add(inputPos.immutable());
    }

    public static void tick(ServerLevel level)
    {
        ResourceKey<Level> dimension = level.dimension();
        Set<BlockPos> pending = DIRTY_INPUTS.get(dimension);
        if (pending == null || pending.isEmpty()) return;

        CableNetwork network = CableNetworkManager.get(level);
        Set<BlockPos> toProcess = new HashSet<>(pending);
        pending.clear();

        for (BlockPos inputPos : toProcess)
        {
            Set<Map.Entry<BlockPos, Direction>> outputs = network.getOutputsForInput(inputPos);
            for (Map.Entry<BlockPos, Direction> edge : outputs)
            {
                BlockPos outputPos = edge.getKey();
                level.neighborChanged(outputPos, level.getBlockState(outputPos).getBlock(), outputPos);
                level.neighborChanged(outputPos.relative(edge.getValue()),
                    level.getBlockState(outputPos.relative(edge.getValue())).getBlock(), outputPos);
            }
        }
    }

    public static void onDimensionUnload(ResourceKey<Level> dimension)
    {
        DIRTY_INPUTS.remove(dimension);
    }
}