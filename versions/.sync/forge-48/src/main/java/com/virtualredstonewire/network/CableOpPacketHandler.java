package com.virtualredstonewire.network;

import com.virtualredstonewire.config.ServerConfig;
import com.virtualredstonewire.data.CableLink;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * v0.3.0 操作请求处理（客户端 -> 服务端）。
 * pull：立即返回全量/追回响应（只读，不占版本号）。
 * add/del：立即完成格式、身份、语义（422 类）校验后入 tick 队列；
 * 状态校验（409/404）与同 tick 冲突由 tick 处理阶段完成。
 * v1.0.0 修复（全量审计）：校验输出面合法性并拒绝同批重复链路；
 * pull 增加每玩家最小间隔（429）；全量/追回响应按 ci/cc 分片下发，
 * 避免单包超过 FriendlyByteBuf 的 32767 字符上限（约 500 条链路即触发）。
 *
 * forge-48 层(面向 48.x/49.x, 即 1.20.2-1.20.4): handle 的上下文形参由
 * Supplier<NetworkEvent.Context> 改为 CustomPayloadEvent.Context; 因 consumerMainThread
 * 已在主线程执行, 去掉内层 enqueueWork 与 setPacketHandled 包装.
 */
public class CableOpPacketHandler
{
    /** v1.0.0：每玩家最近一次 pull 时间（内存驻留，不落盘），用于拉取限流 */
    private static final Map<UUID, Long> LAST_PULL = new HashMap<>();

    public static void handle(CableOpPacket packet, CustomPayloadEvent.Context ctx)
    {
        ServerPlayer sender = ctx.getSender();
        if (sender == null || sender.serverLevel() == null) return;
        ServerLevel level = sender.serverLevel();

        // 格式与身份校验（400）
        if (!level.dimension().location().toString().equals(packet.dimension))
        {
            reject(sender, level, packet, CableProtocol.CODE_BAD_REQUEST,
                "message.virtual_redstone_wire.reject_dimension_mismatch");
            return;
        }
        if (!sender.getName().getString().equals(packet.player))
        {
            reject(sender, level, packet, CableProtocol.CODE_BAD_REQUEST,
                "message.virtual_redstone_wire.reject_player_mismatch");
            return;
        }

        if (packet.isPull())
        {
            if (!allowPull(sender))
            {
                // 限流：仍要回包，否则客户端在途请求无法结算（队列停摆）
                reject(sender, level, packet, CableProtocol.CODE_TOO_MANY,
                    "message.virtual_redstone_wire.reject_pull_too_frequent");
                return;
            }
            CableChangeLog.logRequest(level, packet.toJson(), CableProtocol.CODE_OK);
            sendPullResponse(sender, level, packet.since);
            return;
        }

        // 422 类语义校验（客户端先行校验，服务端兜底）
        String semanticError = validateSemantic(packet, level, sender);
        if (semanticError != null)
        {
            reject(sender, level, packet, CableProtocol.CODE_UNPROCESSABLE, semanticError);
            return;
        }

        if (!CableServerQueue.enqueue(level.dimension(), sender, packet))
        {
            // v1.0.0：排队表超限，拒绝并回包以便客户端结算在途请求
            reject(sender, level, packet, CableProtocol.CODE_TOO_MANY,
                "message.virtual_redstone_wire.reject_queue_full");
        }
    }

    private static String validateSemantic(CableOpPacket packet, ServerLevel level, ServerPlayer sender)
    {
        if (packet.links.isEmpty())
        {
            return "message.virtual_redstone_wire.reject_empty_links";
        }
        if (packet.links.size() > CableProtocol.BATCH_LIMIT)
        {
            return "message.virtual_redstone_wire.reject_batch_too_large";
        }
        double max = ServerConfig.maxLinkDistance.get();
        double maxSq = max * max;
        Set<CableOpPacket.Link> unique = new HashSet<>();
        for (CableOpPacket.Link l : packet.links)
        {
            // v1.0.0：输出面必须是六面之一，否则 Direction.byName 返回 null 并贯穿整条链路
            // （邻居更新 NPE、存档序列化 NPE、全量响应构造 NPE、客户端缓存 NPE）
            if (l.faceDir() == null)
            {
                return "message.virtual_redstone_wire.reject_invalid_face";
            }
            // v1.0.0：同一批内重复链路会让"整批原子"失效（第二条失败时已写入的不回滚也不广播）
            if (!unique.add(l))
            {
                return "message.virtual_redstone_wire.reject_duplicate_link";
            }
            BlockPos fr = l.from();
            BlockPos to = l.to();
            if (fr.equals(to))
            {
                return "message.virtual_redstone_wire.reject_same_pos";
            }
            if (fr.distSqr(to) > maxSq)
            {
                return "message.virtual_redstone_wire.reject_too_far";
            }
            if (sender.blockPosition().distSqr(fr) > maxSq)
            {
                return "message.virtual_redstone_wire.reject_player_too_far";
            }
            if (level.isEmptyBlock(fr))
            {
                return "message.virtual_redstone_wire.reject_start_air";
            }
        }
        return null;
    }

    /** v1.0.0：每玩家 pull 最小间隔，防单客户端刷包（超限回 429 以便客户端结算在途请求） */
    private static boolean allowPull(ServerPlayer sender)
    {
        long now = System.currentTimeMillis();
        UUID id = sender.getUUID();
        Long last = LAST_PULL.get(id);
        if (last != null && now - last < CableProtocol.PULL_MIN_INTERVAL_MS)
        {
            return false;
        }
        LAST_PULL.put(id, now);
        if (LAST_PULL.size() > 64)
        {
            LAST_PULL.entrySet().removeIf(e -> now - e.getValue() > 60000L);
        }
        return true;
    }

    /** 拒绝响应：v1.0.0 起携带该批请求所属维度（此处即发送者当前维度），供客户端按 dm 校验 */
    private static void reject(ServerPlayer sender, ServerLevel level, CableOpPacket packet,
                               int code, String message)
    {
        CableNetworkChannel.sendToPlayer(sender, CableMsgPacket.reject(packet.toJson(), code, message,
            level.dimension().location().toString()));
        CableChangeLog.logRequest(level, packet.toJson(), code);
    }

    /**
     * v1.0.0：全量/追回响应按 FULL_RESPONSE_CHUNK_LINKS 分片下发。
     * 单包载荷受 FriendlyByteBuf.writeUtf 的 32767 字符上限约束（约 500 条链路即溢出并断连），
     * 故此处按条数切分，客户端按 ci/cc 收齐后再应用。
     */
    private static void sendPullResponse(ServerPlayer sender, ServerLevel level, long since)
    {
        var dimension = level.dimension();
        String dim = dimension.location().toString();
        long current = CableNetworkManager.currentVersion(dimension);
        // v1.0.0：追回起点比当前版本还新（跨维度版本号混用等）时按过期处理。
        // 否则会回一个"空变更且未过期"的响应，客户端因版本号回退直接丢弃，缓存永远停在错误内容
        if (since > current)
        {
            CableNetworkChannel.sendToPlayer(sender, CableMsgPacket.fullChunk(current,
                new ArrayList<>(), dim, true, since, 0, 1));
            return;
        }

        List<CableMsgPacket.Change> changes = new ArrayList<>();
        if (since == 0)
        {
            // 全量
            var network = CableNetworkManager.get(level);
            for (CableLink link : network.getAllLinks())
            {
                if (link.getToFace() == null) continue; // 防御：异常数据不进入响应载荷
                changes.add(new CableMsgPacket.Change(CableProtocol.OP_ADD,
                    link.getFrom().getX(), link.getFrom().getY(), link.getFrom().getZ(),
                    link.getTo().getX(), link.getTo().getY(), link.getTo().getZ(),
                    link.getToFace().getName()));
            }
        }
        else
        {
            List<CableNetworkManager.ChangeRecord> rows = CableNetworkManager.getChangesSince(dimension, since);
            if (rows == null)
            {
                CableNetworkChannel.sendToPlayer(sender, CableMsgPacket.fullChunk(current,
                    new ArrayList<>(), dim, true, since, 0, 1));
                return;
            }
            for (CableNetworkManager.ChangeRecord rec : rows)
            {
                if (rec.face() == null) continue; // 防御：异常记录不进入响应载荷
                changes.add(new CableMsgPacket.Change(rec.op(),
                    rec.from().getX(), rec.from().getY(), rec.from().getZ(),
                    rec.to().getX(), rec.to().getY(), rec.to().getZ(),
                    rec.face().getName()));
            }
        }

        int chunkLinks = CableProtocol.FULL_RESPONSE_CHUNK_LINKS;
        int count = Math.max(1, (changes.size() + chunkLinks - 1) / chunkLinks);
        for (int i = 0; i < count; i++)
        {
            int from = i * chunkLinks;
            int to = Math.min(changes.size(), from + chunkLinks);
            List<CableMsgPacket.Change> part = new ArrayList<>(changes.subList(from, to));
            CableNetworkChannel.sendToPlayer(sender,
                CableMsgPacket.fullChunk(current, part, dim, false, since, i, count));
        }
    }
}
