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
 * v0.5.0 增加来源标记（Origin）：撤销/重做请求（UNDO/REDO）与普通操作（NORMAL）共用队列，
 * 成功/失败按来源回调 CableUndoRedoManager；UNDO/REDO 失败不触发增量追回。
 * 全量请求节流：5 秒内不得重复发出；60 秒内超过 10 次视为异常，断开连接并提示
 * （不持久标记，断开后可正常重连）。
 * v1.0.0：历史清空时递增历史代次并丢弃排队中的撤销/重做请求；非当前维度的报文
 * 只作废在途请求并推进队列，不改动缓存与历史。
 * v1.0.0 修复：新增换维度/断线的状态复位入口（onDimensionChanged/reset），
 * 全量请求新增强制通道（跳过 5 秒冷却丢弃），避免一次冷却丢弃导致整个会话不再全量拉取。
 */
public final class CableClientQueue
{
    /** 在途请求来源：普通操作 / 撤销 / 重做 */
    public enum Origin { NORMAL, UNDO, REDO }

    /** 队列元素：请求 + 来源 + 撤销/重做时关联的栈中原始请求 + 入队时的历史代次 */
    private record Pending(CableOpPacket packet, Origin origin, CableOpPacket assoc, int generation) {}

    private static final Queue<Pending> QUEUE = new ArrayDeque<>();
    private static Pending inFlight = null;
    // v1.0.0：历史代次。历史清空时递增，使清空前入队的历史请求不再回写历史
    private static int generation = 0;
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
        enqueue(packet, Origin.NORMAL, packet);
    }

    /** 撤销/重做入队：origin 标记来源，assoc 为栈中关联的原始请求 */
    public static void enqueue(CableOpPacket packet, Origin origin, CableOpPacket assoc)
    {
        QUEUE.add(new Pending(packet, origin, assoc, generation));
        sendNext();
    }

    public static void sendNext()
    {
        if (inFlight != null || QUEUE.isEmpty()) return;
        inFlight = QUEUE.poll();
        if (inFlight.packet().isPull())
        {
            lastPullSc = inFlight.packet().since;
            lastPullDim = inFlight.packet().dimension;
            // v1.0.0：发起新的拉取前丢弃上一批未收齐的分片，避免跨请求拼接
            CableFullSyncBuffer.reset();
        }
        CableNetworkChannel.sendToServer(inFlight.packet());
    }

    /**
     * 发起当前维度全量请求（sc 为 0），带节流：
     * 5 秒内不得重复发出；60 秒内超过 10 次则断开连接并提示，
     * 随后重置计数（不标记用户，保证重连不受影响）。
     */
    public static void requestFull()
    {
        requestFull(false);
    }

    /**
     * v1.0.0：全量请求强制通道。
     * force 为 true 时跳过 5 秒冷却且不计入每分钟风暴计数，用于进入世界、换维度、
     * 过期追回这类必须成功的一次性拉取；否则一次冷却丢弃就会让本次进入维度永远拿不到数据。
     */
    public static void requestFull(boolean force)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (!force && !allowPullRequest())
        {
            return; // 冷却期内丢弃（既有定案：不重试）
        }
        lastFullRequestTime = System.currentTimeMillis();
        enqueue(CableOpPacket.pull(0, mc.level.dimension(), mc.player.getName().getString()));
    }

    /**
     * v1.0.0：带节流的增量追回请求（sc 为当前缓存版本）。
     * 普通操作被拒后的缓存纠正走本入口，使"5 秒冷却 + 每分钟 10 次断开"这套
     * 风暴保护真正覆盖非强制路径（此前该路径直接 enqueue，节流形同虚设）。
     */
    public static boolean requestIncrementalPull()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (!allowPullRequest()) return false;
        lastFullRequestTime = System.currentTimeMillis();
        enqueue(CableOpPacket.pull(ClientCableCache.getVersion(), mc.level.dimension(),
            mc.player.getName().getString()));
        return true;
    }

    /**
     * 节流判定（全量与增量追回共用）：5 秒冷却内拒绝；
     * 60 秒内超过 10 次视为异常，断开连接并提示后重置计数（不标记用户，可正常重连）。
     */
    private static boolean allowPullRequest()
    {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (now - lastFullRequestTime < FULL_COOLDOWN_MS)
        {
            return false;
        }
        while (!FULL_REQUEST_TIMES.isEmpty() && now - FULL_REQUEST_TIMES.peekFirst() > 60000)
        {
            FULL_REQUEST_TIMES.pollFirst();
        }
        if (FULL_REQUEST_TIMES.size() >= FULL_MAX_PER_MINUTE)
        {
            FULL_REQUEST_TIMES.clear();
            lastFullRequestTime = 0;
            if (mc.player != null && mc.player.connection != null)
            {
                mc.player.connection.getConnection().disconnect(Component.translatable(
                    "message.virtual_redstone_wire.full_request_throttle"));
            }
            return false;
        }
        FULL_REQUEST_TIMES.addLast(now);
        return true;
    }

    /**
     * v1.0.0 历史清空联动：作废尚未发出的撤销/重做请求，并使在途的历史请求在响应到达时不再回写历史。
     * 已发往服务端的操作仍然生效（不撤回），缓存由后续广播自然修正。
     */
    public static void onHistoryCleared()
    {
        generation++;
        QUEUE.removeIf(p -> p.origin() != Origin.NORMAL);
    }

    /** v1.0.0：收到非当前维度的报文：作废在途请求并推进队列，不改动缓存与历史 */
    public static void onForeignDimension()
    {
        inFlight = null;
        sendNext();
    }

    /**
     * v1.0.0：换维度时复位队列：作废在途请求，并丢弃排队中尚未发出的请求
     * （它们携带的是旧维度，发出后会被服务端按维度不一致拒绝）。
     */
    public static void onDimensionChanged()
    {
        QUEUE.clear();
        inFlight = null;
        CableFullSyncBuffer.reset();
    }

    /**
     * v1.0.0：断线/登出时复位全部队列状态，避免跨会话残留
     * （残留的在途标记会让重连后所有操作都发不出去）。
     */
    public static void reset()
    {
        QUEUE.clear();
        inFlight = null;
        lastPullSc = 0;
        lastPullDim = null;
        lastFullRequestTime = 0;
        FULL_REQUEST_TIMES.clear();
        CableFullSyncBuffer.reset();
    }

    /**
     * 增量广播（tp 为 d）到达：按内容匹配出队；
     * 匹配成功后先回调撤销管理器（撤销/重做提示），再按需显示链路反馈；
     * 单条请求确认时反馈结果，批量（线缆剪）给出一条汇总反馈。
     * v1.0.0：广播携带请求者名单时，只有自己在该名单内才结算在途请求，
     * 避免把其他玩家对同一链路的成功操作误当成本机请求的确认。
     */
    public static void onDeltaReceived(List<CableMsgPacket.Change> changes, List<String> requesters)
    {
        if (inFlight != null && !inFlight.packet().isPull() && isOwnBatch(requesters))
        {
            boolean batch = inFlight.packet().links.size() > 1;
            Set<CableOpPacket.Link> wanted = new HashSet<>(inFlight.packet().links);
            for (CableMsgPacket.Change c : changes)
            {
                CableOpPacket.Link l = new CableOpPacket.Link(
                    c.fx(), c.fy(), c.fz(), c.tx(), c.ty(), c.tz(), c.face());
                if (c.op().equals(inFlight.packet().op) && wanted.contains(l))
                {
                    Pending done = inFlight;
                    inFlight = null;
                    // v1.0.0：历史清空后到达的历史请求响应不再回写历史
                    if (done.generation() == generation)
                    {
                        CableUndoRedoManager.onOpConfirmed(done.origin(), done.assoc());
                    }
                    if (batch)
                    {
                        if (done.origin() == Origin.NORMAL) showBatchFeedback(done.packet());
                    }
                    else
                    {
                        showOperationFeedback(c);
                    }
                    break;
                }
            }
        }
        sendNext();
    }

    /** v1.0.0：请求者名单为空（旧数据）时按内容结算；否则要求自己在该名单内 */
    private static boolean isOwnBatch(List<String> requesters)
    {
        if (requesters == null || requesters.isEmpty()) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return true;
        return requesters.contains(mc.player.getName().getString());
    }

    /** 批量操作（线缆剪等）在服务端确认后给出汇总反馈，替代操作时的提前提示 */
    private static void showBatchFeedback(CableOpPacket packet)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientConfig.enableChatFeedback.get()) return;
        if (CableUndoRedoManager.isHistoryScreenOpen()) return;
        if (packet.links.isEmpty()) return;
        CableOpPacket.Link first = packet.links.get(0);
        if (CableProtocol.OP_ADD.equals(packet.op))
        {
            mc.player.displayClientMessage(Component.translatable(
                "message.virtual_redstone_wire.batch_created", packet.links.size())
                .withStyle(style -> style.withColor(0x55FF55)), false);
            return;
        }
        boolean sameFrom = true;
        for (CableOpPacket.Link l : packet.links)
        {
            if (l.fx() != first.fx() || l.fy() != first.fy() || l.fz() != first.fz())
            {
                sameFrom = false;
                break;
            }
        }
        net.minecraft.network.chat.MutableComponent text = sameFrom
            ? Component.translatable("message.virtual_redstone_wire.cutter_removed",
                first.fx(), first.fy(), first.fz())
            : Component.translatable("message.virtual_redstone_wire.batch_removed_count",
                packet.links.size());
        mc.player.displayClientMessage(text.withStyle(style -> style.withColor(0x00AA00)), false);
    }

    /** 操作成功反馈：聊天栏显示链路创建/移除结果（含输出端接收面，受聊天反馈开关） */
    private static void showOperationFeedback(CableMsgPacket.Change c)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientConfig.enableChatFeedback.get()) return;
        // 历史窗口页打开期间不弹聊天栏提示（撤销/重做结果在窗口页内实时可见）
        if (CableUndoRedoManager.isHistoryScreenOpen()) return;
        mc.player.displayClientMessage(Component.translatable(
            CableProtocol.OP_ADD.equals(c.op())
                ? "message.virtual_redstone_wire.link_created"
                : "message.virtual_redstone_wire.link_removed",
            c.fx(), c.fy(), c.fz(), c.tx(), c.ty(), c.tz(),
            com.virtualredstonewire.util.FaceNames.of(c.face()))
            .withStyle(style -> style.withColor(
                CableProtocol.OP_ADD.equals(c.op()) ? 0x55FF55 : 0x00AA00)), false);
    }

    /** 全量/追回响应（tp 为 f）到达 */
    public static void onFullReceived()
    {
        // 在途 pull 完成
        inFlight = null;
        sendNext();
    }

    /** 版本号落后（异常）：作废在途请求并强制触发全量（缓存已证明与服务端不一致，不能因节流丢弃） */
    public static void onVersionRollback()
    {
        inFlight = null;
        requestFull(true);
    }

    /** 拒绝响应（tp 为 r）到达：按来源分流，NORMAL 维持既有追回逻辑，UNDO/REDO 不触发追回 */
    public static void onRejectReceived(CableMsgPacket msg)
    {
        Pending done = inFlight;
        inFlight = null;
        Level level = Minecraft.getInstance().level;
        if (level == null)
        {
            // v1.0.0：未进入世界（重生/换维度瞬间）也要推进队列，
            // 否则已排队的请求会被刚作废的在途标记堵死
            sendNext();
            return;
        }

        // 撤销/重做失败：按类型分流（同 tick 相背放回/状态型移除），不触发追回
        if (done != null && done.origin() != Origin.NORMAL)
        {
            // v1.0.0：历史清空后到达的历史请求拒绝响应不再改动历史
            if (done.generation() == generation)
            {
                CableUndoRedoManager.onOpRejected(done.origin(), done.assoc(), msg);
            }
            sendNext();
            return;
        }

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
                mc.player.displayClientMessage(rejectMsg.withStyle(style -> style.withColor(0xFF5555)), false);
            }
        }
        if (msg.code == 404 || msg.code == 409)
        {
            // v1.0.0：其他客户端最近成功操作未超出本机缓存版本时无版本缺口，
            // 失败源于并发修改而非缓存不一致，免拉取并直接按缓存状态定向失效相关历史
            if (msg.lastOp != null && msg.lastOp.version() <= ClientCableCache.getVersion())
            {
                CableUndoRedoManager.invalidateByState();
                sendNext();
            }
            else
            {
                // 状态不一致：增量追回纠正缓存（追回响应按缓存状态定向失效，不再清空全部历史）；
                // v1.0.0：改走带节流的追回入口，使风暴保护覆盖非强制路径
                requestIncrementalPull();
            }
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
