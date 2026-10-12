package com.virtualredstonewire;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import com.virtualredstonewire.data.CableNetworkSavedData;
import com.virtualredstonewire.network.CableNetworkChannel;
import com.virtualredstonewire.network.CableProtocol;
import com.virtualredstonewire.network.CableServerQueue;
import com.virtualredstonewire.network.ProtocolHandshakePacket;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/*
 * 服务端事件。
 *
 * Forge 侧靠事件总线自动订阅，Fabric 侧必须在通用入口显式注册，因此这里提供 register()。
 *
 * 两处平台差异需要注意：
 * 1) Forge 有逐维度的保存事件，Fabric 只有整服保存事件，所以保存时逐维度写回网络数据；
 * 2) Forge 有邻居方块更新事件，Fabric 没有对应事件，该钩子由 Mixin 在邻居通知处调用
 *    onNeighborUpdate。
 */
public class ServerEventHandler
{
    private ServerEventHandler() {}

    /** 注册全部服务端事件（由通用入口调用） */
    public static void register()
    {
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
            RedstoneDiagnostics.runAutoTestIfRequested(server));

        ServerTickEvents.END_SERVER_TICK.register(server ->
        {
            for (ServerLevel level : server.getAllLevels())
            {
                CableServerQueue.processPending(level);
                // 本 API 版本没有可用的保存事件（只有启动/停止与数据包重载），
                // 改为逐 tick 让存档载体指向实时网络并置脏：原版按自己的周期落盘时即可写出最新状态
                onWorldSave(level);
            }
        });

        ServerWorldEvents.LOAD.register((server, level) -> onWorldLoad(level));

        // 协议版本握手：玩家加入时下发本服务端的协议版本，由客户端比对
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            CableNetworkChannel.sendToPlayer(handler.player,
                new ProtocolHandshakePacket(CableProtocol.PROTOCOL_VERSION)));

        ServerWorldEvents.UNLOAD.register((server, level) -> onWorldUnload(level));
    }

    private static void onWorldLoad(ServerLevel serverLevel)
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

    private static void onWorldSave(ServerLevel serverLevel)
    {
        CableNetwork network = CableNetworkManager.get(serverLevel);
        CableNetworkSavedData savedData = CableNetworkSavedData.get(serverLevel);
        savedData.setNetwork(network);
        savedData.setDirty();
    }

    private static void onWorldUnload(ServerLevel serverLevel)
    {
        ResourceKey<Level> dimension = serverLevel.dimension();
        CableNetworkManager.onDimensionUnload(dimension);
        // v1.0.0：同时清掉该维度尚未处理的排队请求，避免常驻离线玩家引用，
        // 以及单人同 JVM 切换存档时旧请求在新存档执行
        CableServerQueue.clearPending(dimension);
        VirtualRedstoneWire.LOGGER.info("Cable network unloaded for dimension: {}",
            dimension.location());
    }

    /**
     * 邻居方块更新钩子。Fabric 没有邻居更新事件，由 Mixin 在 Level 的邻居通知处调用。
     * 语义与 DBW 的 ServerEvents.onBlockUpdate 一致，不做信号源排除。
     */
    public static void onNeighborUpdate(ServerLevel serverLevel, BlockPos pos, Iterable<Direction> notifiedSides)
    {
        CableNetwork network = CableNetworkManager.get(serverLevel);

        // 1) 事件方块本身
        updateSource(serverLevel, network, pos);
        // 2) 所有被通知的邻居（含信号源邻居，如红石块/拉杆/红石线——它们的信号
        //    变化只以"邻居"身份到达输入节点，必须处理，否则拨拉杆/放红石块灯不亮）
        for (Direction dir : notifiedSides)
        {
            updateSource(serverLevel, network, pos.relative(dir));
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

    /**
     * v1.0.0：原先在此清空线缆起点选中态，但该静态表属于客户端状态，
     * 由服务端登出事件清理只在单人同 JVM 下"碰巧"生效（分层泄漏）；
     * 客户端已在 {@code CableClientEvents.onLoggingOut} 中自行清理，故此处移除。
     */
}
