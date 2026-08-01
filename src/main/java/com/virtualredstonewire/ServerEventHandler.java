package com.virtualredstonewire;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import com.virtualredstonewire.data.CableNetworkSavedData;
import com.virtualredstonewire.item.VirtualCableItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
            // 信号不落盘（DBW 语义）：存档加载后对所有输入源主动采集一次真实信号写入存储，
            // 让已放置的红石灯等立即按网络状态点亮
            for (BlockPos inputPos : network.getInputPositions())
            {
                network.refreshSource(serverLevel, inputPos);
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
            VirtualRedstoneWire.LOGGER.info("Cable network unloaded for dimension: {}",
                dimension.location());
        }
    }

    @SubscribeEvent
    public static void onNeighborBlockUpdate(BlockEvent.NeighborNotifyEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockPos pos = event.getPos();
        CableNetwork network = CableNetworkManager.get(serverLevel);
        BlockState state = serverLevel.getBlockState(pos);

        // DBW ServerEvents.onBlockUpdate 语义：
        // 1) 事件方块本身是信号源 → 取其 6 方向 getSignal 最大值写入存储
        if (state.isSignalSource())
        {
            int maxSignal = 0;
            for (Direction dir : Direction.values())
            {
                maxSignal = Math.max(maxSignal, state.getSignal(serverLevel, pos, dir));
            }
            network.setSource(serverLevel, pos, CableNetwork.WORLD_CHANNEL, maxSignal);
        }

        // 2) 被通知邻居（非信号源）→ 取 getBestNeighborSignal 写入存储
        for (Direction dir : event.getNotifiedSides())
        {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = serverLevel.getBlockState(neighborPos);
            if (!neighborState.isSignalSource())
            {
                network.setSource(serverLevel, neighborPos, CableNetwork.WORLD_CHANNEL,
                    serverLevel.getBestNeighborSignal(neighborPos));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        VirtualCableItem.clearSelectedInput((net.minecraft.world.entity.player.Player) event.getEntity());
    }
}
