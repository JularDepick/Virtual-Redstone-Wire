package com.virtualredstonewire.network;

import com.virtualredstonewire.client.gui.CableInfoScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.DistExecutor;

/**
 * 放大镜信息面板查询响应（服务端 -> 客户端）：
 * 携带查询方块坐标与其红石信号强度，交给当前打开的信息面板更新显示。
 * v1.0.0：响应回显查询发生时的维度，客户端在维度不一致时整包丢弃
 * （换维度瞬间的旧结果不再显示在新维度，也不写入新面板）。
 *
 * forge-48 层(面向 48.x/49.x, 即 1.20.2-1.20.4): handle 的上下文形参由
 * Supplier<NetworkEvent.Context> 改为 CustomPayloadEvent.Context; 因 consumerMainThread
 * 已在主线程执行, 去掉内层 enqueueWork 与 setPacketHandled 包装.
 */
public class CableInfoResponsePacket
{
    private final BlockPos pos;
    private final int signal;
    private final boolean actionBar;
    private final boolean error;
    private final String messageKey;
    private final String dimension;

    public CableInfoResponsePacket(BlockPos pos, int signal)
    {
        this(pos, signal, false);
    }

    public CableInfoResponsePacket(BlockPos pos, int signal, boolean actionBar)
    {
        this(pos, signal, actionBar, null);
    }

    public CableInfoResponsePacket(BlockPos pos, int signal, boolean actionBar, String dimension)
    {
        this(pos, signal, actionBar, false, null, dimension);
    }

    /** 查询失败响应（error 为 true），messageKey 为客户端语言键 */
    public CableInfoResponsePacket(BlockPos pos, String messageKey, boolean actionBar)
    {
        this(pos, messageKey, actionBar, null);
    }

    /** 查询失败响应（error 为 true），messageKey 为客户端语言键 */
    public CableInfoResponsePacket(BlockPos pos, String messageKey, boolean actionBar, String dimension)
    {
        this(pos, 0, actionBar, true, messageKey, dimension);
    }

    private CableInfoResponsePacket(BlockPos pos, int signal, boolean actionBar,
                                    boolean error, String messageKey, String dimension)
    {
        this.pos = pos.immutable();
        this.signal = signal;
        this.actionBar = actionBar;
        this.error = error;
        this.messageKey = messageKey;
        this.dimension = dimension;
    }

    public CableInfoResponsePacket(FriendlyByteBuf buf)
    {
        this.pos = buf.readBlockPos();
        this.signal = buf.readVarInt();
        this.actionBar = buf.readBoolean();
        this.error = buf.readBoolean();
        this.messageKey = buf.readBoolean() ? buf.readUtf(256) : null;
        this.dimension = buf.readBoolean() ? buf.readUtf(128) : null;
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.signal);
        buf.writeBoolean(this.actionBar);
        buf.writeBoolean(this.error);
        if (this.messageKey != null)
        {
            buf.writeBoolean(true);
            buf.writeUtf(this.messageKey);
        }
        else
        {
            buf.writeBoolean(false);
        }
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
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
        {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            // v1.0.0：响应维度与当前维度不一致时整包丢弃（换维度瞬间的旧查询结果不再显示）
            if (this.dimension != null && (mc.level == null
                || !this.dimension.equals(mc.level.dimension().location().toString())))
            {
                return;
            }
            if (error)
            {
                // 查询失败：快捷栏上方飘浮显示原因（语言键由服务端指定，红色）
                com.virtualredstonewire.client.CableActionBarHud.show(
                    net.minecraft.network.chat.Component.translatable(messageKey)
                        .withStyle(style -> style.withColor(0xFF5555)));
                return;
            }
            if (actionBar)
            {
                // 快捷栏上方飘浮提示（自定义 HUD）：多段着色——
                // "位置"标签绿、坐标值蓝、"红石信号"标签黄、信号值按强度渐变（0 白，1-15 淡红到纯红）
                com.virtualredstonewire.client.CableActionBarHud.show(
                    net.minecraft.network.chat.Component.translatable(
                        "screen.virtual_redstone_wire.cable_info.position_label")
                        .withStyle(style -> style.withColor(0x55FF55))
                        .append(net.minecraft.network.chat.Component.literal(
                            ": [" + this.pos.getX() + ", " + this.pos.getY() + ", " + this.pos.getZ() + "]")
                            .withStyle(style -> style.withColor(0x5555FF)))
                        .append(net.minecraft.network.chat.Component.literal(" "))
                        .append(net.minecraft.network.chat.Component.translatable(
                            "screen.virtual_redstone_wire.cable_info.signal_label")
                            .withStyle(style -> style.withColor(0xFFFF55)))
                        .append(net.minecraft.network.chat.Component.literal(": "))
                        .append(net.minecraft.network.chat.Component.literal(String.valueOf(this.signal))
                            .withStyle(style -> style.withColor(signalColor(this.signal)))));
                return;
            }
            CableInfoScreen screen = CableInfoScreen.getActiveScreen();
            // v1.0.0：要求面板仍是当前屏幕，避免把结果写进已被替换的旧面板
            if (screen != null && mc.screen == screen && screen.getQueryPos().equals(this.pos))
            {
                screen.onSignalReceived(this.signal);
            }
        });
    }

    /** 信号值配色：0 白色，1-15 从淡红线性渐变到纯红 */
    private static int signalColor(int signal)
    {
        if (signal <= 0) return 0xFFFFFF;
        if (signal >= 15) return 0xFF0000;
        int gb = 204 - (int) (204.0 * signal / 15.0);
        return 0xFF000000 | (0xFF << 16) | (gb << 8) | gb;
    }
}
