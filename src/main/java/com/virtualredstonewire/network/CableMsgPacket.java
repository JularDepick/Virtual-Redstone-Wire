package com.virtualredstonewire.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.virtualredstonewire.client.CableClientQueue;
import com.virtualredstonewire.client.ClientCableCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * v0.3.0 消息（服务端 -> 客户端）。
 * 载荷为 minified JSON 文本（缩写键名）。
 * tp 为 d（增量广播）/ f（全量与追回响应）/ r（拒绝响应）。
 */
public class CableMsgPacket
{
    /** 变更条目（带操作类型） */
    public record Change(String op, int fx, int fy, int fz, int tx, int ty, int tz, String face) {}

    public final String type;
    public final long version;
    public final List<Change> lines;
    public final String dimension;
    public final boolean expired;
    public final String requestJson;
    public final int code;
    public final String message;

    private CableMsgPacket(String type, long version, List<Change> lines, String dimension,
                           boolean expired, String requestJson, int code, String message)
    {
        this.type = type;
        this.version = version;
        this.lines = lines == null ? new ArrayList<>() : lines;
        this.dimension = dimension;
        this.expired = expired;
        this.requestJson = requestJson;
        this.code = code;
        this.message = message;
    }

    public static CableMsgPacket delta(long version, List<Change> lines, String dimension)
    {
        return new CableMsgPacket(CableProtocol.TYPE_DELTA, version, lines, dimension, false, null, 0, null);
    }

    public static CableMsgPacket full(long version, List<Change> lines, String dimension, boolean expired)
    {
        return new CableMsgPacket(CableProtocol.TYPE_FULL, version, lines, dimension, expired, null, 0, null);
    }

    public static CableMsgPacket reject(String requestJson, int code, String message)
    {
        return new CableMsgPacket(CableProtocol.TYPE_REJECT, 0, null, null, false, requestJson, code, message);
    }

    public CableMsgPacket(FriendlyByteBuf buf)
    {
        this(fromJson(buf.readUtf(32767)));
    }

    private CableMsgPacket(CableMsgPacket other)
    {
        this(other.type, other.version, other.lines, other.dimension,
            other.expired, other.requestJson, other.code, other.message);
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeUtf(toJson());
    }

    public boolean isDelta() { return CableProtocol.TYPE_DELTA.equals(type); }
    public boolean isFull() { return CableProtocol.TYPE_FULL.equals(type); }
    public boolean isReject() { return CableProtocol.TYPE_REJECT.equals(type); }

    /** 客户端接收处理（v0.3.0） */
    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            {
                if (isDelta())
                {
                    if (version > ClientCableCache.getVersion())
                    {
                        ClientCableCache.applyDelta(lines, version);
                        CableClientQueue.onDeltaReceived(lines);
                    }
                    else if (version < ClientCableCache.getVersion())
                    {
                        CableClientQueue.onVersionRollback();
                    }
                }
                else if (isFull())
                {
                    // 全量/追回响应到达：撤销/重做历史失效（v0.5.0）
                    com.virtualredstonewire.client.CableUndoRedoManager.clearHistory();
                    if (expired)
                    {
                        com.virtualredstonewire.client.CableClientQueue.requestFull();
                    }
                    else if (CableClientQueue.getLastPullSc() == 0)
                    {
                        ClientCableCache.applyFull(lines, version);
                        CableClientQueue.onFullReceived();
                    }
                    else
                    {
                        ClientCableCache.applyDelta(lines, version);
                        CableClientQueue.onFullReceived();
                    }
                }
                else if (isReject())
                {
                    CableClientQueue.onRejectReceived(this);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }

    private static net.minecraft.resources.ResourceKey<Level> dimensionKey()
    {
        return net.minecraft.client.Minecraft.getInstance().level.dimension();
    }

    public String toJson()
    {
        JsonObject root = new JsonObject();
        root.addProperty(CableProtocol.KEY_TYPE, type);
        if (isDelta() || isFull())
        {
            root.addProperty(CableProtocol.KEY_VERSION, version);
            root.addProperty(CableProtocol.KEY_DIMENSION, dimension);
            JsonArray arr = new JsonArray();
            for (Change c : lines)
            {
                JsonObject o = new JsonObject();
                o.addProperty(CableProtocol.KEY_OP, c.op());
                JsonArray fr = new JsonArray(); fr.add(c.fx()); fr.add(c.fy()); fr.add(c.fz());
                JsonArray to = new JsonArray(); to.add(c.tx()); to.add(c.ty()); to.add(c.tz());
                o.add(CableProtocol.KEY_FROM, fr);
                o.add(CableProtocol.KEY_TO, to);
                o.addProperty(CableProtocol.KEY_FACE, c.face());
                arr.add(o);
            }
            root.add(CableProtocol.KEY_LINES, arr);
            if (isFull())
            {
                root.addProperty(CableProtocol.KEY_EXPIRED, expired);
            }
        }
        else if (isReject())
        {
            if (requestJson != null)
            {
                root.add(CableProtocol.KEY_REQUEST, JsonParser.parseString(requestJson));
            }
            root.addProperty(CableProtocol.KEY_CODE, code);
            root.addProperty(CableProtocol.KEY_MESSAGE, message == null ? "" : message);
        }
        return root.toString();
    }

    private static CableMsgPacket fromJson(String json)
    {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String type = root.get(CableProtocol.KEY_TYPE).getAsString();
        if (CableProtocol.TYPE_REJECT.equals(type))
        {
            String rq = root.has(CableProtocol.KEY_REQUEST)
                ? root.get(CableProtocol.KEY_REQUEST).toString() : null;
            return new CableMsgPacket(type, 0, null, null, false, rq,
                root.get(CableProtocol.KEY_CODE).getAsInt(),
                root.has(CableProtocol.KEY_MESSAGE) ? root.get(CableProtocol.KEY_MESSAGE).getAsString() : null);
        }
        long v = root.get(CableProtocol.KEY_VERSION).getAsLong();
        String dm = root.get(CableProtocol.KEY_DIMENSION).getAsString();
        boolean ex = CableProtocol.TYPE_FULL.equals(type)
            && root.has(CableProtocol.KEY_EXPIRED) && root.get(CableProtocol.KEY_EXPIRED).getAsBoolean();
        List<Change> changes = new ArrayList<>();
        if (root.has(CableProtocol.KEY_LINES))
        {
            for (JsonElement e : root.getAsJsonArray(CableProtocol.KEY_LINES))
            {
                JsonObject o = e.getAsJsonObject();
                JsonArray fr = o.getAsJsonArray(CableProtocol.KEY_FROM);
                JsonArray to = o.getAsJsonArray(CableProtocol.KEY_TO);
                changes.add(new Change(
                    o.get(CableProtocol.KEY_OP).getAsString(),
                    fr.get(0).getAsInt(), fr.get(1).getAsInt(), fr.get(2).getAsInt(),
                    to.get(0).getAsInt(), to.get(1).getAsInt(), to.get(2).getAsInt(),
                    o.get(CableProtocol.KEY_FACE).getAsString()));
            }
        }
        return new CableMsgPacket(type, v, changes, dm, ex, null, 0, null);
    }
}
