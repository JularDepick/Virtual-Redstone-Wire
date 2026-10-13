package com.virtualredstonewire.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/*
 * 网络枢纽。
 *
 * 与 Forge 的差别是结构性的：Forge 用 SimpleChannel 统一登记编解码器并按 Object 泛型投递；
 * 1.20.5 起 Fabric 侧也不再按通道标识收发，改为原版的载荷类型体系：
 * 每个载荷类型必须先在 PayloadTypeRegistry 注册（含其 StreamCodec），收发均以载荷对象为参数。
 * 为把差异挡在这里，各载荷实现 CablePayload（类型常量 + 编解码器），
 * 投递方法因此仍保持泛型形状，所有调用点（服务端队列、客户端队列、配置同步）无需改动。
 *
 * 类型注册必须在模组初始化阶段完成，且两侧都要注册：
 * 客户端要编码 C2S、解码 S2C，专用服务端要解码 C2S、编码 S2C。
 * 接收器必须按侧注册：服务端接收器由通用入口注册，客户端接收器只能由客户端入口注册，
 * 否则专用服务端会去触碰客户端网络类。
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
        // 协议版本握手：Forge 由通道自带，Fabric 侧显式补上
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
        // 协议版本握手：Forge 由通道自带，Fabric 侧显式补上
        ClientPlayNetworking.registerGlobalReceiver(ProtocolHandshakePacket.TYPE,
            ProtocolHandshakePacket::receiveOnClient);
    }

    /*
     * 投递：载荷对象直接交给加载器，编解码与缓冲由注册的 StreamCodec 完成，
     * 不再需要调用方各自构造缓冲（旧实现里每个接收者各建一份缓冲的顾虑随之消失）。
     */

    /** 客户端 -> 服务端 */
    public static void sendToServer(CablePayload packet)
    {
        ClientPlayNetworking.send(packet);
    }

    /** 服务端 -> 单个玩家 */
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

    /** v1.0.0：向全部在线玩家投递（服务端配置变更后的同步广播）。服务器由调用方给出 */
    public static void sendToAllPlayers(MinecraftServer server, CablePayload packet)
    {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            ServerPlayNetworking.send(player, packet);
        }
    }
}
