package com.virtualredstonewire.network;

import com.virtualredstonewire.client.gui.CableInfoScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 放大镜信息面板查询响应（服务端 -> 客户端）：
 * 携带查询方块坐标与其红石信号强度，交给当前打开的信息面板更新显示。
 */
public class CableInfoResponsePacket
{
    private final BlockPos pos;
    private final int signal;
    private final boolean actionBar;

    public CableInfoResponsePacket(BlockPos pos, int signal)
    {
        this(pos, signal, false);
    }

    public CableInfoResponsePacket(BlockPos pos, int signal, boolean actionBar)
    {
        this.pos = pos.immutable();
        this.signal = signal;
        this.actionBar = actionBar;
    }

    public CableInfoResponsePacket(FriendlyByteBuf buf)
    {
        this.pos = buf.readBlockPos();
        this.signal = buf.readVarInt();
        this.actionBar = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.signal);
        buf.writeBoolean(this.actionBar);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            {
                if (actionBar)
                {
                    // 快捷栏上方飘浮提示（自定义 HUD，贴近快捷栏且与物品名称相接不重叠）
                    com.virtualredstonewire.client.CableActionBarHud.show(
                        net.minecraft.network.chat.Component.translatable(
                            "screen.virtual_redstone_wire.cable_info.signal", this.signal).getString());
                    return;
                }
                CableInfoScreen screen = CableInfoScreen.getActiveScreen();
                if (screen != null && screen.getQueryPos().equals(this.pos))
                {
                    screen.onSignalReceived(this.signal);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
