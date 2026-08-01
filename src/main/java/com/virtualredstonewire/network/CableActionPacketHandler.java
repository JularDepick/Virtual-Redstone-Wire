package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.config.ServerConfig;
import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class CableActionPacketHandler
{
    public static void handle(CableActionPacket packet, Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
        {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ServerLevel level = player.serverLevel();
            CableNetwork network = CableNetworkManager.get(level);

            if (packet.isCutter())
            {
                BlockPos pos = packet.getFrom();

                // removeLinksFrom 内部会对每个被删链路清零输出端存储信号并触发邻居更新（灯熄灭）
                int removed = network.removeLinksFrom(pos, level);

                if (removed > 0)
                {
                    CableNetworkManager.markDirty(level);
                    broadcastSync(level, network);
                }
            }
            else
            {
                BlockPos from = packet.getFrom();
                BlockPos to = packet.getTo();

                if (from.equals(to))
                {
                    VirtualRedstoneWire.LOGGER.debug("Player {} attempted to link same position {}",
                        player.getName().getString(), from);
                    return;
                }

                double maxDist = ServerConfig.maxLinkDistance.get();
                if (from.distSqr(to) > maxDist * maxDist)
                {
                    VirtualRedstoneWire.LOGGER.debug("Player {} attempted link from {} to {} exceeding max distance {}",
                        player.getName().getString(), from, to, maxDist);
                    return;
                }

                double playerDistFrom = player.blockPosition().distSqr(from);
                if (playerDistFrom > maxDist * maxDist)
                {
                    VirtualRedstoneWire.LOGGER.debug("Player {} too far from link origin {} (dist squared: {})",
                        player.getName().getString(), from, playerDistFrom);
                    return;
                }

                if (level.isEmptyBlock(from)) return;

                network.toggleLink(from, to, packet.getToFace(), level);
                CableNetworkManager.markDirty(level);

                // 建链后主动采集源方块当前真实信号写入存储（setChannelSignal 变化时
                // 自动触发输出端邻居更新，让红石灯立即点亮）
                network.refreshSource(level, from);

                broadcastSync(level, network);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void broadcastSync(ServerLevel level, CableNetwork network)
    {
        CableNetworkChannel.sendToAll(level,
            new CableSyncPacket(SyncHelper.toEntries(network.getAllLinks())));
    }
}