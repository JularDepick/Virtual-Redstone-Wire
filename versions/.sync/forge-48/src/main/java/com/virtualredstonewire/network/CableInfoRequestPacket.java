package com.virtualredstonewire.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * 放大镜信息面板查询请求（客户端 -> 服务端）：
 * 服务端计算该方块当前的红石信号强度（level.getBestNeighborSignal，
 * 其内部 getSignal 经 mixin 覆写后同样计入虚拟链路信号），单播回
 * CableInfoResponsePacket。信号查询必须走服务端——链路信号存储在
 * 服务端 CableNetwork，且 getSignal 的 mixin 覆写是服务端语义。
 * v1.0.0：请求携带发起时的维度字符串，服务端校验与发送者当前维度一致，
 * 避免换维度瞬间按新维度坐标查询、把旧维度结果回填到新维度面板。
 *
 * forge-48 层(面向 48.x/49.x, 即 1.20.2-1.20.4): handle 的上下文形参由
 * Supplier<NetworkEvent.Context> 改为 CustomPayloadEvent.Context; 因 consumerMainThread
 * 已在主线程执行, 去掉内层 enqueueWork 与 setPacketHandled 包装.
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

        // v1.0.0：请求维度与发送者当前维度不一致（换维度瞬间的迟到请求）时按错误响应处理
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
            // 目标区块未加载，无法计算信号：返回错误响应（客户端飘浮提示原因）
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
