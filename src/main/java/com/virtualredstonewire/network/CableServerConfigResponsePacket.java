package com.virtualredstonewire.network;

import com.virtualredstonewire.client.gui.ConfigScreenTarget;
import com.virtualredstonewire.client.gui.ServerConfigResponseTarget;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 服务端配置读取/修改响应（服务端 -> 客户端，二进制）。
 */
public class CableServerConfigResponsePacket
{
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

    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            {
                /* 服务端配置变更的主动同步：先写入本地 spec（渲染距离与本地预检即时生效），*/
                /* 再交给当前界面刷新显示*/
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
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
