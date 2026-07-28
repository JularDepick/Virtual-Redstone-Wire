package com.virtualredstonewire;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import com.virtualredstonewire.data.CableNetworkSavedData;
import com.virtualredstonewire.redstone.RedstoneCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
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
            for (ServerLevel level :
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getAllLevels())
            {
                RedstoneCalculator.tick(level);
            }
        }
    }

    @SubscribeEvent
    public static void onNeighborBlockUpdate(BlockEvent.NeighborNotifyEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockPos pos = event.getPos();
        CableNetwork network = CableNetworkManager.get(serverLevel);

        if (network.getNode(pos) != null && network.getOutputsForInput(pos).size() > 0)
        {
            RedstoneCalculator.markDirtyInput(pos);
        }

        for (Direction dir : Direction.values())
        {
            BlockPos neighborPos = pos.relative(dir);
            if (network.getNode(neighborPos) != null
                && network.getOutputsForInput(neighborPos).size() > 0)
            {
                RedstoneCalculator.markDirtyInput(neighborPos);
            }
        }
    }
}
