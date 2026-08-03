package com.virtualredstonewire.network;

import com.virtualredstonewire.config.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 服务端配置读取/修改请求（客户端 -> 服务端，二进制）。
 * action 为 get（不带 key，拉取全部配置值）或 set（带 key + value）。
 * 仅存档拥有者（单机/局域网主机玩家，或专用服务器 op 权限 4）可 set，
 * 修改后立即生效并落盘（ServerConfig.SPEC.save()）。
 */
public class CableServerConfigRequestPacket
{
    public static final String ACTION_GET = "get";
    public static final String ACTION_SET = "set";

    private final String action;
    private final String key;
    private final String value;

    public CableServerConfigRequestPacket(String action, String key, String value)
    {
        this.action = action;
        this.key = key == null ? "" : key;
        this.value = value == null ? "" : value;
    }

    public CableServerConfigRequestPacket(FriendlyByteBuf buf)
    {
        this.action = buf.readUtf(16);
        this.key = buf.readUtf(64);
        this.value = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeUtf(action);
        buf.writeUtf(key);
        buf.writeUtf(value);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
        {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || sender.serverLevel() == null) return;
            ServerLevel level = sender.serverLevel();
            boolean owner = level.getServer().isSingleplayerOwner(sender.getGameProfile())
                || sender.hasPermissions(4);

            if (ACTION_GET.equals(action))
            {
                if (!owner)
                {
                    CableNetworkChannel.sendToPlayer(sender,
                        new CableServerConfigResponsePacket(false, "仅存档拥有者可查看服务端配置", null));
                    return;
                }
                CableNetworkChannel.sendToPlayer(sender,
                    new CableServerConfigResponsePacket(true, null, collect()));
                return;
            }

            if (!ACTION_SET.equals(action))
            {
                CableNetworkChannel.sendToPlayer(sender,
                    new CableServerConfigResponsePacket(false, "未知操作", null));
                return;
            }
            if (!owner)
            {
                CableNetworkChannel.sendToPlayer(sender,
                    new CableServerConfigResponsePacket(false, "仅存档拥有者可修改服务端配置", null));
                return;
            }
            String error = apply(key, value);
            if (error != null)
            {
                CableNetworkChannel.sendToPlayer(sender,
                    new CableServerConfigResponsePacket(false, key + ": " + error, null));
                return;
            }
            ServerConfig.SPEC.save();
            Map<String, String> single = new LinkedHashMap<>();
            single.put(key, currentValue(key));
            CableNetworkChannel.sendToPlayer(sender,
                new CableServerConfigResponsePacket(true, null, single));
        });
        ctx.get().setPacketHandled(true);
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
                    if (parsed < 1 || parsed > 1024) return "取值范围 1-1024";
                    ServerConfig.maxLinkDistance.set(parsed);
                    return null;
                }
                case "magnifierRenderDistance":
                {
                    int parsed = Integer.parseInt(v);
                    if (parsed < 64 || parsed > 1024) return "取值范围 64-1024";
                    ServerConfig.magnifierRenderDistance.set(parsed);
                    return null;
                }
                case "networkChangeLogSize":
                {
                    int parsed = Integer.parseInt(v);
                    if (parsed < 200 || parsed > 1000) return "取值范围 200-1000";
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
                    if (v.isEmpty()) return "文件名不能为空";
                    ServerConfig.operationLogFile.set(v);
                    return null;
                case "requestLogEnabled":
                    ServerConfig.requestLogEnabled.set(Boolean.parseBoolean(v));
                    return null;
                case "requestLogFile":
                    if (v.isEmpty()) return "文件名不能为空";
                    ServerConfig.requestLogFile.set(v);
                    return null;
                default:
                    return "未知配置项";
            }
        }
        catch (NumberFormatException e)
        {
            return "数值格式错误";
        }
        catch (IllegalArgumentException e)
        {
            return "取值错误";
        }
    }
}
