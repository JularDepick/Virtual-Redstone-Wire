package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import com.virtualredstonewire.data.CableLink;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class CableQueryPacket
{
    private final BlockPos pos;

    public CableQueryPacket(BlockPos pos)
    {
        this.pos = pos.immutable();
    }

    public CableQueryPacket(FriendlyByteBuf buf)
    {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            CableNetwork network = CableNetworkManager.get(player.level);
            List<String> outgoing = network.queryOutgoing(pos);
            List<String> incoming = network.queryIncoming(pos);

            // 发送查询结果回客户端（以聊天消息形式展示）
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal(
                    "=== Cable Info at [" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "] ==="));
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("Outgoing (" + outgoing.size() + "):"));
            for (String s : outgoing)
            {
                player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("  " + s));
            }
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("Incoming (" + incoming.size() + "):"));
            for (String s : incoming)
            {
                player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("  " + s));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
