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

        CableNetwork network = CableNetworkManager.get(serverLevel);

        // DBW ServerEvents.onBlockUpdate 语义（与 DBW updateSource 一致，不做信号源排除）：
        // 1) 事件方块本身
        updateSource(serverLevel, network, event.getPos());
        // 2) 所有被通知的邻居（含信号源邻居，如红石块/拉杆/红石线——它们的信号
        //    变化只以"邻居"身份到达输入节点，必须处理，否则拨拉杆/放红石块灯不亮）
        for (Direction dir : event.getNotifiedSides())
        {
            updateSource(serverLevel, network, event.getPos().relative(dir));
        }
    }

    /**
     * DBW updateSource 语义：输入节点 pos 的信号源状态变化时，将当前真实信号写入存储。
     * 信号源方块取 6 方向 getSignal 最大值，非信号源取 getBestNeighborSignal。
     */
    private static void updateSource(ServerLevel level, CableNetwork network, BlockPos pos)
    {
        com.virtualredstonewire.data.CableNode node = network.getNode(pos);
        if (node == null || node.getOutgoingCount() == 0) return;

        BlockState state = level.getBlockState(pos);
        int signal;
        if (state.isSignalSource())
        {
            signal = 0;
            for (Direction dir : Direction.values())
            {
                signal = Math.max(signal, state.getSignal(level, pos, dir));
            }
        }
        else
        {
            signal = level.getBestNeighborSignal(pos);
        }
        network.setSource(level, pos, CableNetwork.WORLD_CHANNEL, signal);
    }

    @SubscribeEvent
    public static void onPlayerLogOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        VirtualCableItem.clearSelectedInput((net.minecraft.world.entity.player.Player) event.getEntity());
    }
}
