package com.virtualredstonewire.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * 放大镜信息面板查询请求（客户端 -> 服务端）：
 * CableInfoResponsePacket。信号查询必须走服务端——链路信号存储在
 * handle 的上下文形参由
 */
public class CableInfoRequestPacket
{
    private final BlockPos pos;
    private final boolean actionBar;
    private final String dimension;

    public CableInfoRequestPacket(BlockPos pos)
    {
        this(pos, false, null);
    }

    public CableInfoRequestPacket(BlockPos pos, boolean actionBar)
    {
        this(pos, actionBar, null);
    }

    public CableInfoRequestPacket(BlockPos pos, boolean actionBar, String dimension)
    {
        this.pos = pos.immutable();
        this.actionBar = actionBar;
        this.dimension = dimension;
    }

    public boolean isActionBar()
    {
        return actionBar;
    }

    public String getDimension()
    {
        return dimension;
    }

    public CableInfoRequestPacket(FriendlyByteBuf buf)
    {
        this.pos = buf.readBlockPos();
        this.actionBar = buf.readBoolean();
        this.dimension = buf.readBoolean() ? buf.readUtf(128) : null;
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.actionBar);
        if (this.dimension != null)
        {
            buf.writeBoolean(true);
            buf.writeUtf(this.dimension);
        }
        else
        {
            buf.writeBoolean(false);
        }
    }

    public void handle(CustomPayloadEvent.Context ctx)
    {
        ServerPlayer sender = ctx.getSender();
        if (sender == null || sender.serverLevel() == null) return;
        ServerLevel level = sender.serverLevel();
        String currentDim = level.dimension().location().toString();

        /* 请求维度与发送者当前维度不一致（换维度瞬间的迟到请求）时按错误响应处理*/
        if (this.dimension != null && !this.dimension.equals(currentDim))
        {
            CableNetworkChannel.sendToPlayer(sender,
                new CableInfoResponsePacket(this.pos,
                    "message.virtual_redstone_wire.query_wrong_dimension", this.actionBar,
                    currentDim));
            return;
        }

        if (!level.isLoaded(this.pos))
        {
            /* 目标区块未加载，无法计算信号：返回错误响应（客户端飘浮提示原因）*/
            CableNetworkChannel.sendToPlayer(sender,
                new CableInfoResponsePacket(this.pos,
                    "message.virtual_redstone_wire.query_not_loaded", this.actionBar,
                    currentDim));
            return;
        }

        int signal = level.getBestNeighborSignal(this.pos);

        CableNetworkChannel.sendToPlayer(sender,
            new CableInfoResponsePacket(this.pos, signal, this.actionBar, currentDim));
    }
}
