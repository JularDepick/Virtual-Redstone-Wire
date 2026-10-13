package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.resources.ResourceLocation;
import com.virtualredstonewire.config.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 服务端配置读取/修改请求（客户端 -> 服务端，二进制）。
 * action 为 get（不带 key，拉取全部配置值）或 set（带 key + value）。
 * 仅存档拥有者（单机/局域网主机玩家，或专用服务器 op 权限 4）可 set，
 * 修改后立即生效并落盘（ServerConfig.SPEC.save()）。
 * v1.0.0：请求携带自增 requestId，服务端在响应中回显，客户端据此丢弃非本次请求的回执；
 * 成功修改后服务端向全部在线玩家广播新配置（sync），使已连接客户端的渲染距离与本地预检即时更新。
 */
public class CableServerConfigRequestPacket implements CablePayload
{
    /** 本载荷的类型（通道标识） */
    public static final CustomPacketPayload.Type<CableServerConfigRequestPacket> TYPE =
        new CustomPacketPayload.Type<>(new ResourceLocation(VirtualRedstoneWire.MOD_ID, "config_request"));

    /** 本载荷的编解码器：写入缓冲 / 从缓冲重建 */
    public static final StreamCodec<FriendlyByteBuf, CableServerConfigRequestPacket> CODEC =
        CustomPacketPayload.codec(CableServerConfigRequestPacket::encode, CableServerConfigRequestPacket::new);

    @Override
    public CustomPacketPayload.Type<CableServerConfigRequestPacket> type()
    {
        return TYPE;
    }

    public static final String ACTION_GET = "get";
    public static final String ACTION_SET = "set";

    /** v1.0.0：客户端请求标识自增源（同一连接内唯一即可） */
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    private final String action;
    private final String key;
    private final String value;
    private final int requestId;

    public CableServerConfigRequestPacket(String action, String key, String value)
    {
        this(action, key, value, NEXT_ID.getAndIncrement());
    }

    public CableServerConfigRequestPacket(String action, String key, String value, int requestId)
    {
        this.action = action;
        this.key = key == null ? "" : key;
        this.value = value == null ? "" : value;
        this.requestId = requestId;
    }

    public int getRequestId()
    {
        return requestId;
    }

    public CableServerConfigRequestPacket(FriendlyByteBuf buf)
    {
        this.action = buf.readUtf(16);
        this.key = buf.readUtf(64);
        this.value = buf.readUtf(256);
        this.requestId = buf.readVarInt();
    }

    @Override
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeUtf(action);
        buf.writeUtf(key);
        buf.writeUtf(value);
        buf.writeVarInt(requestId);
    }

    /** 服务端接收器：处理配置读取与修改请求。运行在服务端主线程，无需再回主线程包装 */
    public void handle(ServerPlayer sender)
{
        if (sender == null || sender.serverLevel() == null) return;
        ServerLevel level = sender.serverLevel();
        boolean owner = level.getServer().isSingleplayerOwner(sender.getGameProfile())
            || sender.hasPermissions(4);

        if (ACTION_GET.equals(action))
        {
            if (!owner)
            {
                CableNetworkChannel.sendToPlayer(sender,
                    new CableServerConfigResponsePacket(false,
                        "message.virtual_redstone_wire.config_owner_only_view", null,
                        false, requestId, false, null));
                return;
            }
            CableNetworkChannel.sendToPlayer(sender,
                new CableServerConfigResponsePacket(true, null, collect(),
                    true, requestId, false, null));
            return;
        }

        if (!ACTION_SET.equals(action))
        {
            CableNetworkChannel.sendToPlayer(sender,
                new CableServerConfigResponsePacket(false,
                    "message.virtual_redstone_wire.config_unknown_action", null,
                    false, requestId, false, null));
            return;
        }
        if (!owner)
        {
            CableNetworkChannel.sendToPlayer(sender,
                new CableServerConfigResponsePacket(false,
                    "message.virtual_redstone_wire.config_owner_only_set", null,
                    false, requestId, false, null));
            return;
        }
        String error = apply(key, value);
        if (error != null)
        {
            // 错误响应携带出错配置键与其当前值，供客户端按该键的范围参数本地化显示
            Map<String, String> single = new LinkedHashMap<>();
            single.put(key, currentValue(key));
            CableNetworkChannel.sendToPlayer(sender,
                new CableServerConfigResponsePacket(true, error, single,
                    false, requestId, false, key));
            return;
        }
        ServerConfig.INSTANCE.save();
        Map<String, String> single = new LinkedHashMap<>();
        single.put(key, currentValue(key));
        CableNetworkChannel.sendToPlayer(sender,
            new CableServerConfigResponsePacket(true, null, single,
                true, requestId, false, null));
        // v1.0.0：向全部在线玩家广播新配置，使已连接客户端立即采用新值
        CableNetworkChannel.sendToAllPlayers(sender.getServer(),
            new CableServerConfigResponsePacket(true, null, collect(), true, 0, true, null));
    }

    /** 收集全部服务端配置项的当前值（键名 -> 字符串值） */
    private static Map<String, String> collect()
    {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("maxLinkDistance", String.valueOf(ServerConfig.maxLinkDistance.get()));
        m.put("magnifierRenderDistance", String.valueOf(ServerConfig.magnifierRenderDistance.get()));
        m.put("networkChangeLogSize", String.valueOf(ServerConfig.networkChangeLogSize.get()));
        m.put("operationLogEnabled", String.valueOf(ServerConfig.operationLogEnabled.get()));
        m.put("operationLogSplit",
            ServerConfig.operationLogSplit.get().name().toLowerCase(java.util.Locale.ROOT));
        m.put("operationLogFile", ServerConfig.operationLogFile.get());
        m.put("requestLogEnabled", String.valueOf(ServerConfig.requestLogEnabled.get()));
        m.put("requestLogFile", ServerConfig.requestLogFile.get());
        return m;
    }

    private static String currentValue(String key)
    {
        return collect().getOrDefault(key, "");
    }

    /** 按键设置配置值，返回错误消息（null 表示成功） */
    private static String apply(String key, String value)
    {
        try
        {
            String v = value.trim();
            switch (key)
            {
                case "maxLinkDistance":
                {
                    int parsed = Integer.parseInt(v);
                    if (parsed < 1 || parsed > 1024)
                        return "message.virtual_redstone_wire.config_range";
                    ServerConfig.maxLinkDistance.set(parsed);
                    return null;
                }
                case "magnifierRenderDistance":
                {
                    int parsed = Integer.parseInt(v);
                    if (parsed < 64 || parsed > 1024)
                        return "message.virtual_redstone_wire.config_range";
                    ServerConfig.magnifierRenderDistance.set(parsed);
                    return null;
                }
                case "networkChangeLogSize":
                {
                    int parsed = Integer.parseInt(v);
                    if (parsed < 200 || parsed > 1000)
                        return "message.virtual_redstone_wire.config_range";
                    ServerConfig.networkChangeLogSize.set(parsed);
                    return null;
                }
                case "operationLogEnabled":
                    ServerConfig.operationLogEnabled.set(Boolean.parseBoolean(v));
                    return null;
                case "operationLogSplit":
                    ServerConfig.operationLogSplit.set(
                        ServerConfig.OperationLogSplit.valueOf(v.toUpperCase(java.util.Locale.ROOT)));
                    return null;
                case "operationLogFile":
                    if (v.isEmpty()) return "message.virtual_redstone_wire.config_file_name_empty";
                    ServerConfig.operationLogFile.set(v);
                    return null;
                case "requestLogEnabled":
                    ServerConfig.requestLogEnabled.set(Boolean.parseBoolean(v));
                    return null;
                case "requestLogFile":
                    if (v.isEmpty()) return "message.virtual_redstone_wire.config_file_name_empty";
                    ServerConfig.requestLogFile.set(v);
                    return null;
                default:
                    return "message.virtual_redstone_wire.config_unknown_key";
            }
        }
        catch (NumberFormatException e)
        {
            return "message.virtual_redstone_wire.config_number_format";
        }
        catch (IllegalArgumentException e)
        {
            return "message.virtual_redstone_wire.config_invalid_value";
        }
    }
}
