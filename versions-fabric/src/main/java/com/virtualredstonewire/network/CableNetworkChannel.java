package com.virtualredstonewire.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/*
 * 网络枢纽。
 *
 * 与 Forge 的差别是结构性的：Forge 用 SimpleChannel 统一登记编解码器并按 Object 泛型投递；
 * Fabric 按通道标识注册接收器，发送也必须逐类型进行。为把差异挡在这里，
 * 各载荷实现 CablePayload（通道标识 + 写入缓冲），投递方法因此仍保持泛型形状，
 * 所有调用点（服务端队列、客户端队列、配置同步）无需改动。
 *
 * 接收器必须按侧注册：服务端接收器由通用入口注册，客户端接收器只能由客户端入口注册，
 * 否则专用服务端会去触碰客户端网络类。
 */
public class CableNetworkChannel
{
    private CableNetworkChannel() {}

    /** 服务端接收器：客户端 -> 服务端 */
    public static void registerServer()
    {
        ServerPlayNetworking.registerGlobalReceiver(CableInfoRequestPacket.ID,
            CableInfoRequestPacket::receive);
        ServerPlayNetworking.registerGlobalReceiver(CableServerConfigRequestPacket.ID,
            (server, player, handler, buf, responseSender) ->
                new CableServerConfigRequestPacket(buf).handle(player));
        ServerPlayNetworking.registerGlobalReceiver(CableOpPacket.ID,
            (server, player, handler, buf, responseSender) ->
                CableOpPacketHandler.handle(new CableOpPacket(buf), player));
    }

    /** 客户端接收器：服务端 -> 客户端。只能在客户端调用 */
    public static void registerClient()
    {
        ClientPlayNetworking.registerGlobalReceiver(CableInfoResponsePacket.ID,
            (client, handler, buf, responseSender) ->
                new CableInfoResponsePacket(buf).handle(client));
        ClientPlayNetworking.registerGlobalReceiver(CableMsgPacket.ID,
            (client, handler, buf, responseSender) ->
                new CableMsgPacket(buf).handle(client));
        ClientPlayNetworking.registerGlobalReceiver(CableServerConfigResponsePacket.ID,
            (client, handler, buf, responseSender) ->
                new CableServerConfigResponsePacket(buf).handle(client));
        // 协议版本握手：Forge 由通道自带，Fabric 侧显式补上
        ClientPlayNetworking.registerGlobalReceiver(ProtocolHandshakePacket.ID,
            ProtocolHandshakePacket::receiveOnClient);
    }

    /** 客户端 -> 服务端 */
    public static void sendToServer(CablePayload packet)
    {
        ClientPlayNetworking.send(packet.id(), toBuffer(packet));
    }

    /** 服务端 -> 单个玩家 */
    public static void sendToPlayer(ServerPlayer player, CablePayload packet)
    {
        ServerPlayNetworking.send(player, packet.id(), toBuffer(packet));
    }

    /** 服务端 -> 该维度的全部玩家 */
    public static void sendToAll(ServerLevel level, CablePayload packet)
    {
        for (ServerPlayer player : level.players())
        {
            ServerPlayNetworking.send(player, packet.id(), toBuffer(packet));
        }
    }

    /** v1.0.0：向全部在线玩家投递（服务端配置变更后的同步广播）。服务器由调用方给出 */
    public static void sendToAllPlayers(MinecraftServer server, CablePayload packet)
    {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            ServerPlayNetworking.send(player, packet.id(), toBuffer(packet));
        }
    }

    /*
     * 每个接收者各建一份缓冲：发送时缓冲会被写入报文，
     * 复用同一份可能让后续接收者读到已被消费的内容。
     */
    private static FriendlyByteBuf toBuffer(CablePayload packet)
    {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        packet.encode(buf);
        return buf;
    }
}
