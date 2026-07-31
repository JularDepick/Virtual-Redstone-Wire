package com.virtualredstonewire;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import com.virtualredstonewire.data.CableNetworkSavedData;
import com.virtualredstonewire.item.VirtualCableItem;
import com.virtualredstonewire.redstone.RedstoneCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = VirtualRedstoneWire.MOD_ID)
public class ServerEventHandler
{
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        RedstoneDiagnostics.runAutoTestIfRequested(event.getServer());
    }

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event)
    {
        if (event.getLevel() instanceof ServerLevel serverLevel)
        {
            CableNetworkSavedData savedData = CableNetworkSavedData.get(serverLevel);
            CableNetwork network = savedData.getNetwork();
            CableNetworkManager.load(serverLevel.dimension(), network);
            // 存档加载后强制所有输出端邻居重查信号，让已放置的红石灯/中继器等立即按网络状态点亮
            for (BlockPos outputPos : network.getAllOutputPositions())
            {
                serverLevel.updateNeighborsAt(outputPos, serverLevel.getBlockState(outputPos).getBlock());
            }
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
            ResourceKey<Level> dimension = serverLevel.dimension();
            CableNetworkManager.onDimensionUnload(dimension);
            RedstoneCalculator.onDimensionUnload(dimension);
            VirtualRedstoneWire.LOGGER.info("Cable network unloaded for dimension: {}",
                dimension.location());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END)
        {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            for (ServerLevel level : server.getAllLevels())
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
            VirtualRedstoneWire.LOGGER.info("NeighborNotify: source pos {} has outgoing links, marking dirty", pos);
            RedstoneCalculator.markDirtyInput(serverLevel, pos);
        }

        for (Direction dir : Direction.values())
        {
            BlockPos neighborPos = pos.relative(dir);
            if (network.getNode(neighborPos) != null
                && network.getOutputsForInput(neighborPos).size() > 0)
            {
                VirtualRedstoneWire.LOGGER.info("NeighborNotify: neighbor {} has outgoing links, marking dirty", neighborPos);
                RedstoneCalculator.markDirtyInput(serverLevel, neighborPos);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        VirtualCableItem.clearSelectedInput((net.minecraft.world.entity.player.Player) event.getEntity());
    }
}