package com.virtualredstonewire.redstone;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * v0.4.0 更新传播器：
 * 以"输入节点"为源，沿链路 BFS 级联传播到所有下游输出端并触发邻居更新，
 * 保证 A->B->C 这类链式布线中每个输出端（以及它们旁边的灯）都会重新查询信号。
 */
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
            propagateUpdates(level, inputPos);
        }
    }

    /**
     * 从某个输入节点出发，沿链路 BFS 级联触发所有下游输出端的邻居更新。
     * 供 tick 与诊断器/建链操作共用。
     */
    public static void propagateUpdates(ServerLevel level, BlockPos startInput)
    {
        CableNetwork network = CableNetworkManager.get(level);
        if (network == null || network.getLinkCount() == 0) return;

        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(startInput.immutable());

        while (!queue.isEmpty())
        {
            BlockPos cur = queue.poll();
            if (!visited.add(cur)) continue;

            Set<Map.Entry<BlockPos, Direction>> outputs = network.getOutputsForInput(cur);
            for (Map.Entry<BlockPos, Direction> edge : outputs)
            {
                BlockPos outputPos = edge.getKey();
                level.updateNeighborsAt(outputPos, level.getBlockState(outputPos).getBlock());
                // 输出端若同时是下游输入端（链式布线），继续级联
                if (!network.getOutputsForInput(outputPos).isEmpty())
                {
                    queue.add(outputPos);
                }
            }
        }
    }

    public static void onDimensionUnload(ResourceKey<Level> dimension)
    {
        DIRTY_INPUTS.remove(dimension);
    }
}
