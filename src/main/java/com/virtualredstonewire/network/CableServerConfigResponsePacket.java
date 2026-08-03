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
 * allowed 表示是否被授权；message 为拒绝/错误原因；values 为配置键值集合。
 */
public class CableServerConfigResponsePacket
{
    private final boolean allowed;
    private final String message;
    private final Map<String, String> values;

    public CableServerConfigResponsePacket(boolean allowed, String message, Map<String, String> values)
    {
        this.allowed = allowed;
        this.message = message == null ? "" : message;
        this.values = values == null ? new LinkedHashMap<>() : values;
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
    }

    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            {
                ServerConfigResponseTarget target = ConfigScreenTarget.get();
                if (target != null)
                {
                    target.onServerConfigResponse(allowed, message, values);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
