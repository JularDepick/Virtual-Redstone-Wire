package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * 放大镜信息面板查询请求（客户端 -> 服务端）：
 * CableInfoResponsePacket。信号查询必须走服务端——链路信号存储在
 */
public class CableInfoRequestPacket implements CablePayload
{
    public static final ResourceLocation ID =
        new ResourceLocation(VirtualRedstoneWire.MOD_ID, "info_request");

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

    @Override
    public ResourceLocation id()
    {
        return ID;
    }

    @Override
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

    /** 服务端接收器：处理客户端发来的查询请求 */
    public static void receive(MinecraftServer server, ServerPlayer sender,
                               ServerGamePacketListenerImpl handler,
                               FriendlyByteBuf buf, PacketSender responseSender)
    {
        CableInfoRequestPacket packet = new CableInfoRequestPacket(buf);
        if (sender == null || sender.serverLevel() == null) return;
        ServerLevel level = sender.serverLevel();
        String currentDim = level.dimension().location().toString();

        /* 请求维度与发送者当前维度不一致（换维度瞬间的迟到请求）时按错误响应处理*/
        if (packet.dimension != null && !packet.dimension.equals(currentDim))
        {
            CableNetworkChannel.sendToPlayer(sender,
                new CableInfoResponsePacket(packet.pos,
                    "message.virtual_redstone_wire.query_wrong_dimension", packet.actionBar,
                    currentDim));
            return;
        }

        if (!level.isLoaded(packet.pos))
        {
            /* 目标区块未加载，无法计算信号：返回错误响应（客户端飘浮提示原因）*/
            CableNetworkChannel.sendToPlayer(sender,
                new CableInfoResponsePacket(packet.pos,
                    "message.virtual_redstone_wire.query_not_loaded", packet.actionBar,
                    currentDim));
            return;
        }

        int signal = level.getBestNeighborSignal(packet.pos);

        CableNetworkChannel.sendToPlayer(sender,
            new CableInfoResponsePacket(packet.pos, signal, packet.actionBar, currentDim));
    }
}
