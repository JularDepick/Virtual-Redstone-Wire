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
