package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 协议版本握手（服务端 -> 客户端）。
 *
 * Forge 侧由 SimpleChannel 在建立通道时自带版本校验，两端协议不一致会被直接拒绝；
 * Fabric 侧没有这层机制，因此显式补上：服务端在玩家加入时下发自己的协议版本，
 * 客户端比对不一致就断开连接。
 *
 * 只做服务端到客户端一个方向即可覆盖危险情形：协议不一致的客户端不会继续按
 * 不兼容的报文格式解析链路数据。
 */
public class ProtocolHandshakePacket implements CablePayload
{
    public static final ResourceLocation ID =
        new ResourceLocation(VirtualRedstoneWire.MOD_ID, "handshake");

    private final String protocolVersion;

    public ProtocolHandshakePacket(String protocolVersion)
    {
        this.protocolVersion = protocolVersion;
    }

    public ProtocolHandshakePacket(FriendlyByteBuf buf)
    {
        this.protocolVersion = buf.readUtf(64);
    }

    @Override
    public ResourceLocation id()
    {
        return ID;
    }

    @Override
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeUtf(this.protocolVersion, 64);
    }

    /** 客户端接收器：协议版本不一致时断开，避免按不兼容格式解析报文 */
    public static void receiveOnClient(Minecraft client, ClientPacketListener handler,
                                       FriendlyByteBuf buf, PacketSender responseSender)
    {
        ProtocolHandshakePacket packet = new ProtocolHandshakePacket(buf);
        if (!CableProtocol.PROTOCOL_VERSION.equals(packet.protocolVersion))
        {
            handler.getConnection().disconnect(Component.translatable(
                "message.virtual_redstone_wire.protocol_mismatch",
                packet.protocolVersion, CableProtocol.PROTOCOL_VERSION));
        }
    }
}
