package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class CableNetworkChannel
{
    private static final String PROTOCOL_VERSION = CableProtocol.PROTOCOL_VERSION;
    private static int MESSAGE_ID = 0;

    @SuppressWarnings("removal")
    public static final SimpleChannel CHANNEL =
        NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VirtualRedstoneWire.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register()
    {
        // 0: 信息面板信号查询请求（v0.2.1 功能，保留）
        CHANNEL.messageBuilder(CableInfoRequestPacket.class, MESSAGE_ID++)
            .encoder(CableInfoRequestPacket::encode)
            .decoder(CableInfoRequestPacket::new)
            .consumerMainThread(CableInfoRequestPacket::handle)
            .add();

        // 1: 信息面板信号查询响应（v0.2.1 功能，保留）
        CHANNEL.messageBuilder(CableInfoResponsePacket.class, MESSAGE_ID++)
            .encoder(CableInfoResponsePacket::encode)
            .decoder(CableInfoResponsePacket::new)
            .consumerMainThread(CableInfoResponsePacket::handle)
            .add();

        // 2: v0.3.0 操作请求（add/del/pull，JSON 载荷）
        CHANNEL.messageBuilder(CableOpPacket.class, MESSAGE_ID++)
            .encoder(CableOpPacket::encode)
            .decoder(CableOpPacket::new)
            .consumerMainThread(CableOpPacketHandler::handle)
            .add();

        // 3: v0.3.0 消息（d/f/r，JSON 载荷）
        CHANNEL.messageBuilder(CableMsgPacket.class, MESSAGE_ID++)
            .encoder(CableMsgPacket::encode)
            .decoder(CableMsgPacket::new)
            .consumerMainThread(CableMsgPacket::handle)
            .add();

        // 4: 服务端配置读取/修改请求（二进制）
        CHANNEL.messageBuilder(CableServerConfigRequestPacket.class, MESSAGE_ID++)
            .encoder(CableServerConfigRequestPacket::encode)
            .decoder(CableServerConfigRequestPacket::new)
            .consumerMainThread(CableServerConfigRequestPacket::handle)
            .add();

        // 5: 服务端配置读取/修改响应（二进制）
        CHANNEL.messageBuilder(CableServerConfigResponsePacket.class, MESSAGE_ID++)
            .encoder(CableServerConfigResponsePacket::encode)
            .decoder(CableServerConfigResponsePacket::new)
            .consumerMainThread(CableServerConfigResponsePacket::handle)
            .add();
    }

    public static void sendToAll(ServerLevel level, Object packet)
    {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    public static void sendToServer(Object packet)
    {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
