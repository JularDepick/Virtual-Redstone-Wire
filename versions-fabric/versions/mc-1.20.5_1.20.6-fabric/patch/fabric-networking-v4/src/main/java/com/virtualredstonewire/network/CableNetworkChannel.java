package com.virtualredstonewire.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/*
 * 网络枢纽。
 * 每个载荷类型必须先在 PayloadTypeRegistry 注册（含其 StreamCodec），收发均以载荷对象为参数。
 * 类型注册必须在模组初始化阶段完成，且两侧都要注册：
 */
public class CableNetworkChannel
{
    private CableNetworkChannel() {}

    /** 载荷类型注册（客户端 -> 服务端与 服务端 -> 客户端 两个方向） */
    public static void registerPayloads()
    {
        PayloadTypeRegistry.playC2S().register(CableOpPacket.TYPE, CableOpPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(CableInfoRequestPacket.TYPE, CableInfoRequestPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(CableServerConfigRequestPacket.TYPE,
            CableServerConfigRequestPacket.CODEC);

        PayloadTypeRegistry.playS2C().register(CableMsgPacket.TYPE, CableMsgPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(CableInfoResponsePacket.TYPE, CableInfoResponsePacket.CODEC);
        PayloadTypeRegistry.playS2C().register(CableServerConfigResponsePacket.TYPE,
            CableServerConfigResponsePacket.CODEC);
        /* 协议版本握手：Forge 由通道自带，Fabric 侧显式补上*/
        PayloadTypeRegistry.playS2C().register(ProtocolHandshakePacket.TYPE, ProtocolHandshakePacket.CODEC);
    }

    /** 服务端接收器：客户端 -> 服务端 */
    public static void registerServer()
    {
        ServerPlayNetworking.registerGlobalReceiver(CableInfoRequestPacket.TYPE,
            CableInfoRequestPacket::receive);
        ServerPlayNetworking.registerGlobalReceiver(CableServerConfigRequestPacket.TYPE,
            (packet, context) -> packet.handle(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(CableOpPacket.TYPE,
            (packet, context) -> CableOpPacketHandler.handle(packet, context.player()));
    }

    /** 客户端接收器：服务端 -> 客户端。只能在客户端调用 */
    public static void registerClient()
    {
        ClientPlayNetworking.registerGlobalReceiver(CableInfoResponsePacket.TYPE,
            (packet, context) -> packet.handle(context.client()));
        ClientPlayNetworking.registerGlobalReceiver(CableMsgPacket.TYPE,
            (packet, context) -> packet.handle(context.client()));
        ClientPlayNetworking.registerGlobalReceiver(CableServerConfigResponsePacket.TYPE,
            (packet, context) -> packet.handle(context.client()));
        /* 协议版本握手：Forge 由通道自带，Fabric 侧显式补上*/
        ClientPlayNetworking.registerGlobalReceiver(ProtocolHandshakePacket.TYPE,
            ProtocolHandshakePacket::receiveOnClient);
    }

    /*
     * 投递：载荷对象直接交给加载器，编解码与缓冲由注册的 StreamCodec 完成，
     * 不再需要调用方各自构造缓冲（旧实现里每个接收者各建一份缓冲的顾虑随之消失）。
     */

    public static void sendToServer(CablePayload packet)
    {
        ClientPlayNetworking.send(packet);
    }

    public static void sendToPlayer(ServerPlayer player, CablePayload packet)
    {
        ServerPlayNetworking.send(player, packet);
    }

    /** 服务端 -> 该维度的全部玩家 */
    public static void sendToAll(ServerLevel level, CablePayload packet)
    {
        for (ServerPlayer player : level.players())
        {
            ServerPlayNetworking.send(player, packet);
        }
    }

    /** 向全部在线玩家投递（服务端配置变更后的同步广播）。服务器由调用方给出 */
    public static void sendToAllPlayers(MinecraftServer server, CablePayload packet)
    {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            ServerPlayNetworking.send(player, packet);
        }
    }
}
