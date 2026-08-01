package com.virtualredstonewire.client;

import com.virtualredstonewire.network.CableMsgPacket;
import com.virtualredstonewire.network.CableNetworkChannel;
import com.virtualredstonewire.network.CableOpPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * v0.3.0 客户端操作任务队列：串行发送，同一时刻至多一个在途请求。
 * 出队判定：收到增量广播且包含自己请求的链路条目（fr/to/fc 与 op 匹配），或收到拒绝响应；
 * 版本号落后（异常）时作废在途请求并触发全量。
 */
public final class CableClientQueue
{
    private static final Queue<CableOpPacket> QUEUE = new ArrayDeque<>();
    private static CableOpPacket inFlight = null;
    private static long lastPullSc = 0;
    private static String lastPullDim = null;

    private CableClientQueue() {}

    public static boolean hasInFlight()
    {
        return inFlight != null;
    }

    public static void enqueue(CableOpPacket packet)
    {
        QUEUE.add(packet);
        sendNext();
    }

    public static void sendNext()
    {
        if (inFlight != null || QUEUE.isEmpty()) return;
        inFlight = QUEUE.poll();
        if (inFlight.isPull())
        {
            lastPullSc = inFlight.since;
            lastPullDim = inFlight.dimension;
        }
        CableNetworkChannel.sendToServer(inFlight);
    }

    /** 增量广播（tp 为 d）到达：按内容匹配出队 */
    public static void onDeltaReceived(List<CableMsgPacket.Change> changes)
    {
        if (inFlight != null && !inFlight.isPull())
        {
            Set<CableOpPacket.Link> wanted = new HashSet<>(inFlight.links);
            for (CableMsgPacket.Change c : changes)
            {
                CableOpPacket.Link l = new CableOpPacket.Link(
                    c.fx(), c.fy(), c.fz(), c.tx(), c.ty(), c.tz(), c.face());
                if (c.op().equals(inFlight.op) && wanted.contains(l))
                {
                    inFlight = null;
                    break;
                }
            }
        }
        sendNext();
    }

    /** 全量/追回响应（tp 为 f）到达 */
    public static void onFullReceived()
    {
        // 在途 pull 完成
        inFlight = null;
        sendNext();
    }

    /** 版本号落后（异常）：作废在途请求并触发全量 */
    public static void onVersionRollback()
    {
        inFlight = null;
        Level level = Minecraft.getInstance().level;
        if (level != null)
        {
            enqueue(CableOpPacket.pull(0, level.dimension(),
                Minecraft.getInstance().player.getName().getString()));
        }
    }

    /** 拒绝响应（tp 为 r）到达：按错误码分流 */
    public static void onRejectReceived(CableMsgPacket msg)
    {
        inFlight = null;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        if (msg.code == 404 || msg.code == 409)
        {
            // 状态不一致：增量追回纠正缓存
            enqueue(CableOpPacket.pull(ClientCableCache.getVersion(), level.dimension(),
                Minecraft.getInstance().player.getName().getString()));
        }
        // 422/400：直接放弃或记录（由上层提示）
        else
        {
            sendNext();
        }
    }

    public static long getLastPullSc()
    {
        return lastPullSc;
    }

    public static String getLastPullDim()
    {
        return lastPullDim;
    }
}
