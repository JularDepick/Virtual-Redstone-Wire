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
 * 服务端操作队列：按 tick 边界收集 add/del 请求，统一处理。
 */
public final class CableServerQueue
{
    private static final Map<ResourceKey<Level>, List<QueuedOp>> PENDING = new HashMap<>();

    public record QueuedOp(ServerPlayer player, CableOpPacket packet) {}

    private CableServerQueue() {}

    /** 入队；超过 PENDING_QUEUE_LIMIT 时返回 false（调用方按 429 拒绝） */
    public static boolean enqueue(ResourceKey<Level> dim, ServerPlayer player, CableOpPacket packet)
    {
        List<QueuedOp> list = PENDING.computeIfAbsent(dim, k -> new ArrayList<>());
        if (list.size() >= CableProtocol.PENDING_QUEUE_LIMIT)
        {
            return false;
        }
        list.add(new QueuedOp(player, packet));
        return true;
    }

    /** 维度卸载时清理排队表，避免常驻离线玩家引用与跨存档串包 */
    public static void clearPending(ResourceKey<Level> dim)
    {
        PENDING.remove(dim);
    }

    /** 服务端每 tick 调用：处理该维度本 tick 收集的请求 */
    public static void processPending(ServerLevel level)
    {
        ResourceKey<Level> dim = level.dimension();
        List<QueuedOp> batch = PENDING.remove(dim);
        if (batch == null || batch.isEmpty()) return;

        /* 按目标链路集合分组*/
        Map<Set<CableOpPacket.Link>, List<QueuedOp>> groups = new LinkedHashMap<>();
        for (QueuedOp op : batch)
        {
            groups.computeIfAbsent(new HashSet<>(op.packet().links), k -> new ArrayList<>()).add(op);
        }

        /* 相交即视为同目标 —— 同一链路在不同分组间出现 add 与 del 时，涉及的分组整批不执行*/
        Set<CableOpPacket.Link> conflicted = new HashSet<>();
        Map<CableOpPacket.Link, String> opByLink = new HashMap<>();
        for (List<QueuedOp> members : groups.values())
        {
            for (QueuedOp m : members)
            {
                for (CableOpPacket.Link l : m.packet().links)
                {
                    String seen = opByLink.putIfAbsent(l, m.packet().op);
                    if (seen != null && !seen.equals(m.packet().op))
                    {
                        conflicted.add(l);
                    }
                }
            }
        }

        for (List<QueuedOp> members : groups.values())
        {
            String op0 = members.get(0).packet().op;
            boolean mixed = false;
            for (QueuedOp m : members)
            {
                if (!m.packet().op.equals(op0)) { mixed = true; break; }
            }
            if (mixed || intersects(members.get(0).packet().links, conflicted))
            {
                /* 相背：全部不执行*/
                for (QueuedOp op : members)
                {
                    sendReject(level, op, CableProtocol.CODE_CONFLICT,
                        "message.virtual_redstone_wire.reject_conflict_same_tick");
                }
                continue;
            }

            CableOpPacket req = members.get(0).packet();
            CableNetwork network = CableNetworkManager.get(level);

            /* 状态校验（整批原子）*/
            if (CableProtocol.OP_ADD.equals(op0))
            {
                boolean exists = req.links.stream().anyMatch(l ->
                    l.faceDir() == null
                        || (network.getNode(l.from()) != null
                            && network.getNode(l.from()).hasOutgoingFace(l.to(), l.faceDir())));
                if (exists)
                {
                    for (QueuedOp op : members)
                    {
                        sendReject(level, op, CableProtocol.CODE_CONFLICT,
                            "message.virtual_redstone_wire.reject_already_exists");
                    }
                    continue;
                }
            }
            else
            {
                boolean missing = req.links.stream().anyMatch(l ->
                    l.faceDir() == null
                        || network.getNode(l.from()) == null
                        || !network.getNode(l.from()).hasOutgoingFace(l.to(), l.faceDir()));
                if (missing)
                {
                    for (QueuedOp op : members)
                    {
                        sendReject(level, op, CableProtocol.CODE_NOT_EXISTS,
                            "message.virtual_redstone_wire.reject_not_exists");
                    }
                    continue;
                }
            }

            /* 执行（整批原子：中途失败回滚已写入的链路，避免服务端与客户端缓存永久分叉）*/
            List<CableOpPacket.Link> applied = new ArrayList<>();
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
                applied.add(l);
            }
            if (!allOk)
            {
                for (int i = applied.size() - 1; i >= 0; i--)
                {
                    CableOpPacket.Link l = applied.get(i);
                    if (CableProtocol.OP_ADD.equals(op0))
                    {
                        network.removeLink(l.from(), l.to(), l.faceDir(), level);
                    }
                    else
                    {
                        network.addLink(l.from(), l.to(), l.faceDir(), level);
                    }
                }
                /* 已校验通过仍失败，防御性拒绝*/
                for (QueuedOp op : members)
                {
                    sendReject(level, op, CableProtocol.CODE_CONFLICT,
                        "message.virtual_redstone_wire.reject_batch_failed");
                }
                continue;
            }

            /* 版本号 + 变更表 + 广播 + 日志*/
            long v = CableNetworkManager.nextVersion(dim);
            CableNetworkManager.markDirty(level);
            String playerName = members.get(0).player.getName().getString();
            Set<String> participants = new HashSet<>();
            for (QueuedOp m : members)
            {
                participants.add(m.player().getName().getString());
            }
            List<CableMsgPacket.Change> changes = new ArrayList<>();
            for (CableOpPacket.Link l : req.links)
            {
                changes.add(new CableMsgPacket.Change(op0,
                    l.fx(), l.fy(), l.fz(), l.tx(), l.ty(), l.tz(), l.face()));
                CableNetworkManager.recordChange(dim, new CableNetworkManager.ChangeRecord(
                    v, playerName, op0, l.from(), l.to(), l.faceDir()));
            }
            CableNetworkChannel.sendToAll(level,
                CableMsgPacket.delta(v, changes, dim.location().toString(),
                    new ArrayList<>(participants)));
            /* 合并批次按全部参与者写日志，避免日志取证失真*/
            for (QueuedOp m : members)
            {
                CableChangeLog.logOperation(level, m.player().getName().getString(), op0,
                    m.packet().links, "success");
                CableChangeLog.logRequest(level, m.packet().toJson(), CableProtocol.CODE_OK);
            }
            /* 记录该维度最近一次成功操作摘要（协议拒绝响应 lx 字段的数据源）*/
            List<CableNetworkManager.LastOperation.LinkEntry> entries = new ArrayList<>();
            for (CableOpPacket.Link l : req.links)
            {
                entries.add(new CableNetworkManager.LastOperation.LinkEntry(
                    l.from(), l.to(), l.faceDir()));
            }
            CableNetworkManager.recordLastOperation(dim,
                new CableNetworkManager.LastOperation(v, playerName, op0, participants, entries));
        }
    }

    private static boolean intersects(List<CableOpPacket.Link> links, Set<CableOpPacket.Link> other)
    {
        if (other.isEmpty()) return false;
        for (CableOpPacket.Link l : links)
        {
            if (other.contains(l)) return true;
        }
        return false;
    }

    /** 拒绝响应：level 取该批请求所属维度（不取处理时刻玩家所在维度，避免跨维度错配） */
    private static void sendReject(ServerLevel level, QueuedOp op, int code, String message)
    {
        String playerName = op.player().getName().getString();
        /* 404/409 附带其他客户端最近一次成功操作的摘要（无记录或操作者本人时省略）*/
        CableMsgPacket.Concurrent lastOp = null;
        if (code == CableProtocol.CODE_NOT_EXISTS || code == CableProtocol.CODE_CONFLICT)
        {
            lastOp = CableMsgPacket.concurrentOf(
                CableNetworkManager.getLastOperation(level.dimension()), playerName);
        }
        CableNetworkChannel.sendToPlayer(op.player(),
            CableMsgPacket.reject(op.packet().toJson(), code, message, lastOp,
                level.dimension().location().toString()));
        CableChangeLog.logOperation(level, playerName,
            op.packet().op, op.packet().links, "rejected");
        CableChangeLog.logRequest(level, op.packet().toJson(), code);
    }
}
