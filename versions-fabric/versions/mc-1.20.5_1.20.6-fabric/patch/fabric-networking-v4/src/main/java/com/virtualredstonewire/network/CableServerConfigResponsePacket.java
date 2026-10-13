package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.resources.ResourceLocation;
import com.virtualredstonewire.client.gui.ConfigScreenTarget;
import com.virtualredstonewire.client.gui.ServerConfigResponseTarget;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 服务端配置读取/修改响应（服务端 -> 客户端，二进制）。
 * allowed 表示是否被授权访问服务端配置；message 为拒绝/错误原因；values 为配置键值集合。
 * v1.0.0：新增 success（本次操作是否成功，与权限分离）、requestId（回显请求标识，0 表示主动同步）、
 * sync（服务端配置变更后的广播同步，客户端据此刷新本地 spec 的渲染距离与预检值）、
 * errorKey（出错配置键，供客户端按该键的取值范围本地化提示）。
 */
public class CableServerConfigResponsePacket implements CablePayload
{
    /** 本载荷的类型（通道标识） */
    public static final CustomPacketPayload.Type<CableServerConfigResponsePacket> TYPE =
        new CustomPacketPayload.Type<>(new ResourceLocation(VirtualRedstoneWire.MOD_ID, "config_response"));

    /** 本载荷的编解码器：写入缓冲 / 从缓冲重建 */
    public static final StreamCodec<FriendlyByteBuf, CableServerConfigResponsePacket> CODEC =
        CustomPacketPayload.codec(CableServerConfigResponsePacket::encode,
            CableServerConfigResponsePacket::new);

    @Override
    public CustomPacketPayload.Type<CableServerConfigResponsePacket> type()
    {
        return TYPE;
    }

    private final boolean allowed;
    private final String message;
    private final Map<String, String> values;
    private final boolean success;
    private final int requestId;
    private final boolean sync;
    private final String errorKey;

    public CableServerConfigResponsePacket(boolean allowed, String message, Map<String, String> values)
    {
        this(allowed, message, values, allowed, 0, false, null);
    }

    public CableServerConfigResponsePacket(boolean allowed, String message, Map<String, String> values,
                                           boolean success, int requestId, boolean sync, String errorKey)
    {
        this.allowed = allowed;
        this.message = message == null ? "" : message;
        this.values = values == null ? new LinkedHashMap<>() : values;
        this.success = success;
        this.requestId = requestId;
        this.sync = sync;
        this.errorKey = errorKey;
    }

    public CableServerConfigResponsePacket(FriendlyByteBuf buf)
    {
        this.allowed = buf.readBoolean();
        this.message = buf.readUtf(256);
        int size = buf.readVarInt();
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++)
        {
            map.put(buf.readUtf(64), buf.readUtf(256));
        }
        this.values = map;
        this.success = buf.readBoolean();
        this.requestId = buf.readVarInt();
        this.sync = buf.readBoolean();
        this.errorKey = buf.readBoolean() ? buf.readUtf(64) : null;
    }

    @Override
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBoolean(allowed);
        buf.writeUtf(message);
        buf.writeVarInt(values.size());
        for (Map.Entry<String, String> e : values.entrySet())
        {
            buf.writeUtf(e.getKey());
            buf.writeUtf(e.getValue());
        }
        buf.writeBoolean(success);
        buf.writeVarInt(requestId);
        buf.writeBoolean(sync);
        if (errorKey != null)
        {
            buf.writeBoolean(true);
            buf.writeUtf(errorKey);
        }
        else
        {
            buf.writeBoolean(false);
        }
    }

    /** 客户端接收器：只在客户端注册，侧判定与回主线程包装都不再需要 */
    public void handle(net.minecraft.client.Minecraft client)
    {
        // v1.0.0：服务端配置变更的主动同步：先写入本地 spec（渲染距离与本地预检即时生效），
        // 再交给当前界面刷新显示
        if (sync)
        {
            com.virtualredstonewire.config.ServerConfig.applyFromMap(values);
        }
        ServerConfigResponseTarget target = ConfigScreenTarget.get();
        if (target != null)
        {
            target.onServerConfigResponse(requestId, allowed, message, values, sync, errorKey,
                success);
        }
    }
}
