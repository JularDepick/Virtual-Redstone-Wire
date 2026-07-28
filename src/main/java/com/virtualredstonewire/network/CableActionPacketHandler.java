package com.virtualredstonewire.network;

import com.virtualredstonewire.block.VirtualRedstoneSourceBlock;
import com.virtualredstonewire.config.ServerConfig;
import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import com.virtualredstonewire.redstone.RedstoneCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
                List<Map.Entry<BlockPos, Direction>> savedOutgoing = new ArrayList<>();
                var node = network.getNode(pos);
                if (node != null)
                {
                    savedOutgoing.addAll(node.getOutgoing());
                }

                int removed = network.removeLinksFrom(pos);

                if (removed > 0)
                {
                    for (Map.Entry<BlockPos, Direction> edge : savedOutgoing)
                    {
                        removeVirtualSourceIfEmpty(level, network, edge.getKey(), edge.getValue());
                    }
                    CableNetworkManager.markDirty(level);
                    broadcastSync(level, network);
                }
            }
            else
            {
                BlockPos from = packet.getFrom();
                BlockPos to = packet.getTo();
                if (from.equals(to)) return;

                double distance = Math.sqrt(from.distSqr(to));
                if (distance > ServerConfig.maxLinkDistance.get()) return;

                if (level.isEmptyBlock(from)) return;

                boolean created = network.toggleLink(from, to, packet.getToFace());
                CableNetworkManager.markDirty(level);

                if (created)
                {
                    placeVirtualSource(level, to, packet.getToFace());
                    RedstoneCalculator.markDirtyInput(from);
                }
                else
                {
                    removeVirtualSourceIfEmpty(level, network, to, packet.getToFace());
                }

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

    private static void placeVirtualSource(ServerLevel level, BlockPos toPos, Direction toFace)
    {
        BlockPos sourcePos = toPos.relative(toFace);
        BlockState existing = level.getBlockState(sourcePos);
        if (!existing.isAir())
        {
            if (existing.getBlock() instanceof VirtualRedstoneSourceBlock)
            {
                return;
            }
            return;
        }
        level.setBlock(sourcePos,
            com.virtualredstonewire.registry.ModBlocks.VIRTUAL_SOURCE.get().defaultBlockState()
                .setValue(VirtualRedstoneSourceBlock.FACING, toFace.getOpposite())
                .setValue(VirtualRedstoneSourceBlock.POWER, 0),
            2);
    }

    private static void removeVirtualSourceIfEmpty(ServerLevel level, CableNetwork network,
                                                    BlockPos toPos, Direction toFace)
    {
        BlockPos sourcePos = toPos.relative(toFace);
        if (!network.hasOutputAt(toPos, toFace))
        {
            BlockState existing = level.getBlockState(sourcePos);
            if (existing.getBlock() instanceof VirtualRedstoneSourceBlock)
            {
                level.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }
}
