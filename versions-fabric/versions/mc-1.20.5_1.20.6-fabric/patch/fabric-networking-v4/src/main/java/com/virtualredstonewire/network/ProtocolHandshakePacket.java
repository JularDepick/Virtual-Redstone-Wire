package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 协议版本握手（服务端 -> 客户端）。
 */
public class ProtocolHandshakePacket implements CablePayload
{
    /** 本载荷的类型（通道标识） */
    public static final CustomPacketPayload.Type<ProtocolHandshakePacket> TYPE =
        new CustomPacketPayload.Type<>(new ResourceLocation(VirtualRedstoneWire.MOD_ID, "handshake"));

    /** 本载荷的编解码器：写入缓冲 / 从缓冲重建 */
    public static final StreamCodec<FriendlyByteBuf, ProtocolHandshakePacket> CODEC =
        CustomPacketPayload.codec(ProtocolHandshakePacket::encode, ProtocolHandshakePacket::new);

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
    public CustomPacketPayload.Type<ProtocolHandshakePacket> type()
    {
        return TYPE;
    }

    @Override
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeUtf(this.protocolVersion, 64);
    }

    /** 客户端接收器：协议版本不一致时断开，避免按不兼容格式解析报文 */
    public static void receiveOnClient(ProtocolHandshakePacket packet, ClientPlayNetworking.Context context)
    {
        if (!CableProtocol.PROTOCOL_VERSION.equals(packet.protocolVersion))
        {
            /* 载荷只会在已进入游戏时收到，连接必然存在；*/
            /* Minecraft#getConnection 返回的是客户端包监听器，再取一层才是底层连接*/
            Connection connection = context.client().getConnection().getConnection();
            connection.disconnect(Component.translatable(
                "message.virtual_redstone_wire.protocol_mismatch",
                packet.protocolVersion, CableProtocol.PROTOCOL_VERSION));
        }
    }
}
