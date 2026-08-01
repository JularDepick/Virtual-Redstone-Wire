package com.virtualredstonewire.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 放大镜信息面板查询请求（客户端 -> 服务端）：
 * 服务端计算该方块当前的红石信号强度（level.getBestNeighborSignal，
 * 其内部 getSignal 经 mixin 覆写后同样计入虚拟链路信号），单播回
 * CableInfoResponsePacket。信号查询必须走服务端——链路信号存储在
 * 服务端 CableNetwork，且 getSignal 的 mixin 覆写是服务端语义。
 */
public class CableInfoRequestPacket
{
    private final BlockPos pos;

    public CableInfoRequestPacket(BlockPos pos)
    {
        this.pos = pos.immutable();
    }

    public CableInfoRequestPacket(FriendlyByteBuf buf)
    {
        this.pos = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(this.pos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
        {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || sender.serverLevel() == null) return;
            ServerLevel level = sender.serverLevel();

            int signal = level.getBestNeighborSignal(this.pos);

            CableNetworkChannel.sendToPlayer(sender,
                new CableInfoResponsePacket(this.pos, signal));
        });
        ctx.get().setPacketHandled(true);
    }
}
