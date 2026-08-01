package com.virtualredstonewire.network;

import com.virtualredstonewire.config.ServerConfig;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * v0.3.0 操作请求处理（客户端 -> 服务端）。
 * pull：立即返回全量/追回响应（只读，不占版本号）。
 * add/del：立即完成格式、身份、语义（422 类）校验后入 tick 队列；
 * 状态校验（409/404）与同 tick 冲突由 tick 处理阶段完成。
 */
public class CableOpPacketHandler
{
    public static void handle(CableOpPacket packet, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
        {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || sender.serverLevel() == null) return;
            ServerLevel level = sender.serverLevel();

            // 格式与身份校验（400）
            if (!level.dimension().location().toString().equals(packet.dimension))
            {
                reject(sender, packet, CableProtocol.CODE_BAD_REQUEST, "维度与发送者所在维度不一致");
                return;
            }
            if (!sender.getName().getString().equals(packet.player))
            {
                reject(sender, packet, CableProtocol.CODE_BAD_REQUEST, "玩家名称与发送者不一致");
                return;
            }

            if (packet.isPull())
            {
                CableNetworkChannel.sendToPlayer(sender, buildPullResponse(level, packet.since));
                return;
            }

            // 422 类语义校验（客户端先行校验，服务端兜底）
            String semanticError = validateSemantic(packet, level, sender);
            if (semanticError != null)
            {
                reject(sender, packet, CableProtocol.CODE_UNPROCESSABLE, semanticError);
                return;
            }

            CableServerQueue.enqueue(level.dimension(), sender, packet);
        });
        ctx.get().setPacketHandled(true);
    }

    private static String validateSemantic(CableOpPacket packet, ServerLevel level, ServerPlayer sender)
    {
        if (packet.links.isEmpty())
        {
            return "链路列表为空";
        }
        if (packet.links.size() > CableProtocol.BATCH_LIMIT)
        {
            return "批量超过 " + CableProtocol.BATCH_LIMIT + " 条";
        }
        double max = ServerConfig.maxLinkDistance.get();
        double maxSq = max * max;
        for (CableOpPacket.Link l : packet.links)
        {
            BlockPos fr = l.from();
            BlockPos to = l.to();
            if (fr.equals(to))
            {
                return "起点与终点相同";
            }
            if (fr.distSqr(to) > maxSq)
            {
                return "起点到终点超过距离上限";
            }
            if (sender.blockPosition().distSqr(fr) > maxSq)
            {
                return "玩家与起点距离超过上限";
            }
            if (level.isEmptyBlock(fr))
            {
                return "起点方块为空气";
            }
        }
        return null;
    }

    private static void reject(ServerPlayer sender, CableOpPacket packet, int code, String message)
    {
        CableNetworkChannel.sendToPlayer(sender, CableMsgPacket.reject(packet.toJson(), code, message));
    }

    private static CableMsgPacket buildPullResponse(ServerLevel level, long since)
    {
        var dimension = level.dimension();
        List<CableNetworkManager.ChangeRecord> rows = CableNetworkManager.getChangesSince(dimension, since);
        if (since == 0)
        {
            // 全量
            var network = CableNetworkManager.get(level);
            List<CableMsgPacket.Change> changes = new java.util.ArrayList<>();
            for (com.virtualredstonewire.data.CableLink link : network.getAllLinks())
            {
                changes.add(new CableMsgPacket.Change(CableProtocol.OP_ADD,
                    link.getFrom().getX(), link.getFrom().getY(), link.getFrom().getZ(),
                    link.getTo().getX(), link.getTo().getY(), link.getTo().getZ(),
                    link.getToFace().getName()));
            }
            return CableMsgPacket.full(CableNetworkManager.currentVersion(dimension), changes,
                dimension.location().toString(), false);
        }
        if (rows == null)
        {
            return CableMsgPacket.full(CableNetworkManager.currentVersion(dimension),
                new java.util.ArrayList<>(), dimension.location().toString(), true);
        }
        List<CableMsgPacket.Change> changes = new java.util.ArrayList<>();
        for (CableNetworkManager.ChangeRecord rec : rows)
        {
            changes.add(new CableMsgPacket.Change(rec.op(),
                rec.from().getX(), rec.from().getY(), rec.from().getZ(),
                rec.to().getX(), rec.to().getY(), rec.to().getZ(),
                rec.face().getName()));
        }
        return CableMsgPacket.full(CableNetworkManager.currentVersion(dimension), changes,
            dimension.location().toString(), false);
    }
}
