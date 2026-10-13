package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * 网络通道注册与投递 (forge-48 层, 面向 48.x/49.x, 即 1.20.2-1.20.4).
 *
 * 基线 1.20.1 用的 net.minecraftforge.network.simple.SimpleChannel 包与
 * NetworkRegistry.newSimpleChannel 在新网络体系中已不存在: 通道创建改为
 * ChannelBuilder.named(name).networkProtocolVersion(int).simpleChannel();
 * 包注册链 (messageBuilder / encoder / decoder / consumerMainThread / add) 形状不变.
 *
 * 四处签名变化:
 *   1) networkProtocolVersion 收 int, 故对 CableProtocol.PROTOCOL_VERSION 取整数;
 *      两侧版本校验默认即 exact(version), 与基线 newSimpleChannel 的 equals 语义一致
 *      (同样要求对端是已声明的 modded 连接, 不接受 vanilla 或缺失该通道).
 *   2) PacketDistributor.with 收具体值而非 Supplier (基线传 level::dimension 与 () -> player).
 *   3) Channel.send 的参数顺序为 (message, target), 与基线的 (target, message) 相反.
 *   4) 新体系不再有 sendToServer, 客户端发往服务端改用 PacketDistributor.SERVER.noArg().
 *
 * 对外行为与基线完全一致: 通道名, 6 个包的判别号与注册顺序, 版本校验口径,
 * 以及 sendToAll / sendToServer / sendToPlayer / sendToAllPlayers 四个入口的名字与语义均未变.
 */
public class CableNetworkChannel
{
    /** 协议版本以整数形式交给 ChannelBuilder (基线为字符串等值比较) */
    private static final int PROTOCOL_VERSION = Integer.parseInt(CableProtocol.PROTOCOL_VERSION);
    private static int MESSAGE_ID = 0;

    // ResourceLocation(String, String) 在本区间已被标记为待删除(基线同样如此并抑制该注记);
    // 该构造器在 1.20.2-1.20.4 仍可用, 故保留原写法而不改用更高版本才有的新工厂方法.
    @SuppressWarnings("removal")
    public static final SimpleChannel CHANNEL =
        ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(VirtualRedstoneWire.MOD_ID, "main"))
            .networkProtocolVersion(PROTOCOL_VERSION)
            .simpleChannel();

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
        CHANNEL.send(packet, PacketDistributor.DIMENSION.with(level.dimension()));
    }

    public static void sendToServer(Object packet)
    {
        CHANNEL.send(packet, PacketDistributor.SERVER.noArg());
    }

    public static void sendToPlayer(ServerPlayer player, Object packet)
    {
        CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }

    /** v1.0.0：向全部在线玩家投递（服务端配置变更后的同步广播） */
    public static void sendToAllPlayers(Object packet)
    {
        CHANNEL.send(packet, PacketDistributor.ALL.noArg());
    }
}
