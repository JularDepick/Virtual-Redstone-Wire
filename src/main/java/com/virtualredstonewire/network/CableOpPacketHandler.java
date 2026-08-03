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
                reject(sender, packet, CableProtocol.CODE_BAD_REQUEST,
                    "message.virtual_redstone_wire.reject_dimension_mismatch");
                return;
            }
            if (!sender.getName().getString().equals(packet.player))
            {
                reject(sender, packet, CableProtocol.CODE_BAD_REQUEST,
                    "message.virtual_redstone_wire.reject_player_mismatch");
                return;
            }

            if (packet.isPull())
            {
                CableChangeLog.logRequest(level, packet.toJson(), CableProtocol.CODE_OK);
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
            return "message.virtual_redstone_wire.reject_empty_links";
        }
        if (packet.links.size() > CableProtocol.BATCH_LIMIT)
        {
            return "message.virtual_redstone_wire.reject_batch_too_large";
        }
        double max = ServerConfig.maxLinkDistance.get();
        double maxSq = max * max;
        for (CableOpPacket.Link l : packet.links)
        {
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

    private static void reject(ServerPlayer sender, CableOpPacket packet, int code, String message)
    {
        CableNetworkChannel.sendToPlayer(sender, CableMsgPacket.reject(packet.toJson(), code, message));
        CableChangeLog.logRequest(sender.serverLevel(), packet.toJson(), code);
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
