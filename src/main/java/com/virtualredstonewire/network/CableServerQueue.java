package com.virtualredstonewire.network;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v0.3.0 服务端操作队列：按 tick 边界收集 add/del 请求，统一处理。
 * 同 tick 冲突规则：目标与类型相同合并执行一次；目标相同但类型相背全部不执行；
 * 不同目标的合法请求各自独立执行，各自占一个版本号并各自广播。
 */
public final class CableServerQueue
{
    private static final Map<ResourceKey<Level>, List<QueuedOp>> PENDING = new HashMap<>();

    public record QueuedOp(ServerPlayer player, CableOpPacket packet) {}

    private CableServerQueue() {}

    public static void enqueue(ResourceKey<Level> dim, ServerPlayer player, CableOpPacket packet)
    {
        PENDING.computeIfAbsent(dim, k -> new ArrayList<>()).add(new QueuedOp(player, packet));
    }

    /** 服务端每 tick 调用：处理该维度本 tick 收集的请求 */
    public static void processPending(ServerLevel level)
    {
        ResourceKey<Level> dim = level.dimension();
        List<QueuedOp> batch = PENDING.remove(dim);
        if (batch == null || batch.isEmpty()) return;

        // 按目标链路集合分组
        Map<Set<CableOpPacket.Link>, List<QueuedOp>> groups = new LinkedHashMap<>();
        for (QueuedOp op : batch)
        {
            groups.computeIfAbsent(new HashSet<>(op.packet().links), k -> new ArrayList<>()).add(op);
        }

        for (List<QueuedOp> members : groups.values())
        {
            String op0 = members.get(0).packet().op;
            boolean mixed = false;
            for (QueuedOp m : members)
            {
                if (!m.packet().op.equals(op0)) { mixed = true; break; }
            }
            if (mixed)
            {
                // 相背：全部不执行
                for (QueuedOp op : members)
                {
                    sendReject(op, CableProtocol.CODE_CONFLICT, "同 tick 相背请求未执行");
                }
                continue;
            }

            CableOpPacket req = members.get(0).packet();
            CableNetwork network = CableNetworkManager.get(level);

            // 状态校验（整批原子）
            if (CableProtocol.OP_ADD.equals(op0))
            {
                boolean exists = req.links.stream().anyMatch(l ->
                    network.getNode(l.from()) != null
                        && network.getNode(l.from()).hasOutgoingFace(l.to(), l.faceDir()));
                if (exists)
                {
                    for (QueuedOp op : members)
                    {
                        sendReject(op, CableProtocol.CODE_CONFLICT, "目标链路已存在");
                    }
                    continue;
                }
            }
            else
            {
                boolean missing = req.links.stream().anyMatch(l ->
                    network.getNode(l.from()) == null
                        || !network.getNode(l.from()).hasOutgoingFace(l.to(), l.faceDir()));
                if (missing)
                {
                    for (QueuedOp op : members)
                    {
                        sendReject(op, CableProtocol.CODE_NOT_EXISTS, "目标链路不存在");
                    }
                    continue;
                }
            }

            // 执行（整批原子）
            boolean allOk = true;
            for (CableOpPacket.Link l : req.links)
            {
                boolean ok = CableProtocol.OP_ADD.equals(op0)
                    ? network.addLink(l.from(), l.to(), l.faceDir(), level)
                    : network.removeLink(l.from(), l.to(), l.faceDir(), level);
                if (!ok)
                {
                    allOk = false;
                    break;
                }
            }
            if (!allOk)
            {
                // 已校验通过仍失败，防御性拒绝
                for (QueuedOp op : members)
                {
                    sendReject(op, CableProtocol.CODE_CONFLICT, "批量执行失败");
                }
                continue;
            }

            // 版本号 + 变更表 + 广播 + 日志
            long v = CableNetworkManager.nextVersion(dim);
            CableNetworkManager.markDirty(level);
            String playerName = members.get(0).player.getName().getString();
            List<CableMsgPacket.Change> changes = new ArrayList<>();
            for (CableOpPacket.Link l : req.links)
            {
                changes.add(new CableMsgPacket.Change(op0,
                    l.fx(), l.fy(), l.fz(), l.tx(), l.ty(), l.tz(), l.face()));
                CableNetworkManager.recordChange(dim, new CableNetworkManager.ChangeRecord(
                    v, playerName, op0, l.from(), l.to(), l.faceDir()));
            }
            CableNetworkChannel.sendToAll(level,
                CableMsgPacket.delta(v, changes, dim.location().toString()));
            CableChangeLog.log(level, v, playerName, op0, req.links, "success");
        }
    }

    private static void sendReject(QueuedOp op, int code, String message)
    {
        CableNetworkChannel.sendToPlayer(op.player(),
            CableMsgPacket.reject(op.packet().toJson(), code, message));
        ServerLevel level = op.player().serverLevel();
        CableChangeLog.log(level, CableNetworkManager.currentVersion(level.dimension()),
            op.player().getName().getString(), op.packet().op, op.packet().links, "rejected");
    }
}
