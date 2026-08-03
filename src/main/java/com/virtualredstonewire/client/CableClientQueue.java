package com.virtualredstonewire.client;

import com.virtualredstonewire.config.ClientConfig;
import com.virtualredstonewire.network.CableMsgPacket;
import com.virtualredstonewire.network.CableNetworkChannel;
import com.virtualredstonewire.network.CableOpPacket;
import com.virtualredstonewire.network.CableProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * v0.3.0 客户端操作任务队列：串行发送，同一时刻至多一个在途请求。
 * 出队判定：收到增量广播且包含自己请求的链路条目（fr/to/fc 与 op 匹配），或收到拒绝响应；
 * 版本号落后（异常）时作废在途请求并触发全量。
 * 全量请求节流：5 秒内不得重复发出；60 秒内超过 10 次视为异常，断开连接并提示
 * （不持久标记，断开后可正常重连）。
 */
public final class CableClientQueue
{
    private static final Queue<CableOpPacket> QUEUE = new ArrayDeque<>();
    private static CableOpPacket inFlight = null;
    private static long lastPullSc = 0;
    private static String lastPullDim = null;

    // 全量请求节流
    private static final long FULL_COOLDOWN_MS = 5000;
    private static final int FULL_MAX_PER_MINUTE = 10;
    private static long lastFullRequestTime = 0;
    private static final Deque<Long> FULL_REQUEST_TIMES = new ArrayDeque<>();

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

    /**
     * 发起当前维度全量请求（sc 为 0），带节流：
     * 5 秒内不得重复发出；60 秒内超过 10 次则断开连接并提示，
     * 随后重置计数（不标记用户，保证重连不受影响）。
     */
    public static void requestFull()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        if (now - lastFullRequestTime < FULL_COOLDOWN_MS)
        {
            return; // 冷却期内丢弃
        }
        while (!FULL_REQUEST_TIMES.isEmpty() && now - FULL_REQUEST_TIMES.peekFirst() > 60000)
        {
            FULL_REQUEST_TIMES.pollFirst();
        }
        if (FULL_REQUEST_TIMES.size() >= FULL_MAX_PER_MINUTE)
        {
            // 异常：断开连接并提示，重置计数保证重连正常
            FULL_REQUEST_TIMES.clear();
            lastFullRequestTime = 0;
            if (mc.player.connection != null)
            {
                mc.player.connection.getConnection().disconnect(Component.translatable(
                    "message.virtual_redstone_wire.full_request_throttle"));
            }
            return;
        }
        FULL_REQUEST_TIMES.addLast(now);
        lastFullRequestTime = now;
        enqueue(CableOpPacket.pull(0, mc.level.dimension(), mc.player.getName().getString()));
    }

    /** 增量广播（tp 为 d）到达：按内容匹配出队；单条请求确认时反馈结果，批量（线缆剪）不逐条提示 */
    public static void onDeltaReceived(List<CableMsgPacket.Change> changes)
    {
        if (inFlight != null && !inFlight.isPull())
        {
            boolean batch = inFlight.links.size() > 1;
            Set<CableOpPacket.Link> wanted = new HashSet<>(inFlight.links);
            for (CableMsgPacket.Change c : changes)
            {
                CableOpPacket.Link l = new CableOpPacket.Link(
                    c.fx(), c.fy(), c.fz(), c.tx(), c.ty(), c.tz(), c.face());
                if (c.op().equals(inFlight.op) && wanted.contains(l))
                {
                    inFlight = null;
                    if (!batch)
                    {
                        showOperationFeedback(c);
                    }
                    break;
                }
            }
        }
        sendNext();
    }

    /** 操作成功反馈：聊天栏显示链路创建/移除结果（含输出端接收面，受聊天反馈开关） */
    private static void showOperationFeedback(CableMsgPacket.Change c)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientConfig.enableChatFeedback.get()) return;
        mc.player.sendSystemMessage(Component.translatable(
            CableProtocol.OP_ADD.equals(c.op())
                ? "message.virtual_redstone_wire.link_created"
                : "message.virtual_redstone_wire.link_removed",
            c.fx(), c.fy(), c.fz(), c.tx(), c.ty(), c.tz(), c.face())
            .withStyle(style -> style.withColor(
                CableProtocol.OP_ADD.equals(c.op()) ? 0x55FF55 : 0x00AA00)));
    }

    /** 全量/追回响应（tp 为 f）到达 */
    public static void onFullReceived()
    {
        // 在途 pull 完成
        inFlight = null;
        sendNext();
    }

    /** 版本号落后（异常）：作废在途请求并触发全量（带节流） */
    public static void onVersionRollback()
    {
        inFlight = null;
        requestFull();
    }

    /** 拒绝响应（tp 为 r）到达：飘浮反馈拒绝原因，按错误码分流 */
    public static void onRejectReceived(CableMsgPacket msg)
    {
        inFlight = null;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        // 操作结果反馈：聊天栏显示服务端拒绝原因（语言键本地化，错误反馈始终可见，红色）
        if (msg.message != null && !msg.message.isEmpty())
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null)
            {
                net.minecraft.network.chat.MutableComponent rejectMsg =
                    "message.virtual_redstone_wire.reject_batch_too_large".equals(msg.message)
                        ? Component.translatable(msg.message, CableProtocol.BATCH_LIMIT)
                        : Component.translatable(msg.message);
                mc.player.sendSystemMessage(rejectMsg.withStyle(style -> style.withColor(0xFF5555)));
            }
        }
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
