package com.virtualredstonewire;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import com.virtualredstonewire.data.CableNetworkSavedData;
import com.virtualredstonewire.redstone.RedstoneCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.NeighborBlockUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VirtualRedstoneWire.MOD_ID)
public class ServerEventHandler
{
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event)
    {
        if (event.getLevel() instanceof ServerLevel serverLevel)
        {
            CableNetworkSavedData savedData = CableNetworkSavedData.get(serverLevel);
            CableNetworkManager.load(serverLevel.dimension(), savedData.getNetwork());
            VirtualRedstoneWire.LOGGER.info("Cable network loaded for dimension: {}",
                serverLevel.dimension().location());
        }
    }

    @SubscribeEvent
    public static void onWorldSave(LevelEvent.Save event)
    {
        if (event.getLevel() instanceof ServerLevel serverLevel)
        {
            CableNetwork network = CableNetworkManager.get(serverLevel);
            CableNetworkSavedData savedData = CableNetworkSavedData.get(serverLevel);
            savedData.setNetwork(network);
            savedData.setDirty();
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event)
    {
        if (event.getLevel() instanceof ServerLevel serverLevel)
        {
            CableNetworkManager.onDimensionUnload(serverLevel.dimension());
            VirtualRedstoneWire.LOGGER.info("Cable network unloaded for dimension: {}",
                serverLevel.dimension().location());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END)
        {
            for (ServerLevel level : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getAllLevels())
            {
                RedstoneCalculator.tick(level);
            }
        }
    }

    @SubscribeEvent
    public static void onNeighborBlockUpdate(NeighborBlockUpdateEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockPos pos = event.getPos();
        Direction updateDirection = Direction.values()[(event.getPos().hashCode() & 3) % 6];
        // 检查是否有输入方块被更新
        CableNetwork network = CableNetworkManager.get(serverLevel);

        // 当pos是某个link的输入方块时，标记其所有输出
        var outputs = network.getOutputsForInput(pos);
        if (!outputs.isEmpty())
        {
            for (var output : outputs)
            {
                RedstoneCalculator.markDirty(output.getKey(), output.getValue());
            }
        }

        // 当pos的邻居是某个link的输入方块时，也需要触发
        for (Direction dir : Direction.values())
        {
            BlockPos neighborPos = pos.relative(dir);
            var neighborOutputs = network.getOutputsForInput(neighborPos);
            if (!neighborOutputs.isEmpty())
            {
                for (var output : neighborOutputs)
                {
                    RedstoneCalculator.markDirty(output.getKey(), output.getValue());
                }
            }
        }
    }
}
