package com.virtualredstonewire.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.virtualredstonewire.client.CableClientQueue;
import com.virtualredstonewire.client.CableUndoRedoManager;
import com.virtualredstonewire.client.ClientCableCache;
import com.virtualredstonewire.data.CableNetworkManager;
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
 * v1.0.0：拒绝响应在 cd 为 404/409 时附带 lx（其他客户端最近一次成功操作摘要）；
 * 增量追回（sc 大于 0）不再清空撤销/重做双栈，改为按缓存状态定向失效。
 */
public class CableMsgPacket
{
    /** 变更条目（带操作类型） */
    public record Change(String op, int fx, int fy, int fz, int tx, int ty, int tz, String face) {}

    /** v1.0.0 其他客户端最近一次成功操作的摘要（拒绝响应 lx 字段） */
    public record Concurrent(String player, int code, long version, String op, List<Change> lines) {}

    public final String type;
    public final long version;
    public final List<Change> lines;
    public final String dimension;
    public final boolean expired;
    public final String requestJson;
    public final int code;
    public final String message;
    public final Concurrent lastOp;

    private CableMsgPacket(String type, long version, List<Change> lines, String dimension,
                           boolean expired, String requestJson, int code, String message,
                           Concurrent lastOp)
    {
        this.type = type;
        this.version = version;
        this.lines = lines == null ? new ArrayList<>() : lines;
        this.dimension = dimension;
        this.expired = expired;
        this.requestJson = requestJson;
        this.code = code;
        this.message = message;
        this.lastOp = lastOp;
    }

    public static CableMsgPacket delta(long version, List<Change> lines, String dimension)
    {
        return new CableMsgPacket(CableProtocol.TYPE_DELTA, version, lines, dimension,
            false, null, 0, null, null);
    }

    public static CableMsgPacket full(long version, List<Change> lines, String dimension, boolean expired)
    {
        return new CableMsgPacket(CableProtocol.TYPE_FULL, version, lines, dimension,
            expired, null, 0, null, null);
    }

    public static CableMsgPacket reject(String requestJson, int code, String message)
    {
        return reject(requestJson, code, message, null);
    }

    /** v1.0.0：拒绝响应可附带其他客户端最近一次成功操作的摘要 */
    public static CableMsgPacket reject(String requestJson, int code, String message, Concurrent lastOp)
    {
        return new CableMsgPacket(CableProtocol.TYPE_REJECT, 0, null, null,
            false, requestJson, code, message, lastOp);
    }

    /**
     * v1.0.0：由服务端最近成功操作摘要构造 lx 载荷；
     * 无摘要、或操作者就是请求者本人（不算其他客户端）时返回 null。
     */
    public static Concurrent concurrentOf(CableNetworkManager.LastOperation last, String requesterName)
    {
        if (last == null || last.player().equals(requesterName)) return null;
        List<Change> changes = new ArrayList<>();
        for (CableNetworkManager.LastOperation.LinkEntry e : last.links())
        {
            changes.add(new Change(last.op(), e.from().getX(), e.from().getY(), e.from().getZ(),
                e.to().getX(), e.to().getY(), e.to().getZ(), e.face().getName()));
        }
        return new Concurrent(last.player(), CableProtocol.CODE_OK, last.version(), last.op(), changes);
    }

    /** v1.0.0：lx 携带的链路是否与给定链路集合相交（用于失败原因细分） */
    public static boolean touches(Concurrent last, List<CableOpPacket.Link> links)
    {
        if (last == null || links == null) return false;
        for (Change c : last.lines())
        {
            for (CableOpPacket.Link l : links)
            {
                if (l.fx() == c.fx() && l.fy() == c.fy() && l.fz() == c.fz()
                    && l.tx() == c.tx() && l.ty() == c.ty() && l.tz() == c.tz()
                    && l.face().equals(c.face()))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public CableMsgPacket(FriendlyByteBuf buf)
    {
        this(fromJson(buf.readUtf(32767)));
    }

    private CableMsgPacket(CableMsgPacket other)
    {
        this(other.type, other.version, other.lines, other.dimension,
            other.expired, other.requestJson, other.code, other.message, other.lastOp);
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeUtf(toJson());
    }

    public boolean isDelta() { return CableProtocol.TYPE_DELTA.equals(type); }
    public boolean isFull() { return CableProtocol.TYPE_FULL.equals(type); }
    public boolean isReject() { return CableProtocol.TYPE_REJECT.equals(type); }

    /** 客户端接收处理（v1.0.0） */
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
                    // sc 为 0 为全量请求响应，其余为增量追回响应
                    boolean fullPull = CableClientQueue.getLastPullSc() == 0;
                    // 全量与过期标记：缓存整体重建或已失效，前提全面失效，清空双栈
                    if (fullPull || expired)
                    {
                        CableUndoRedoManager.clearHistory();
                    }
                    if (expired)
                    {
                        // 过期响应已使本次追回请求终结：先复位在途请求，再发起全量请求，
                        // 否则新请求会因在途未复位而滞留队列，需等下一次广播才发出
                        CableClientQueue.onFullReceived();
                        CableClientQueue.requestFull();
                    }
                    else if (fullPull)
                    {
                        ClientCableCache.applyFull(lines, version);
                        CableClientQueue.onFullReceived();
                    }
                    else
                    {
                        // 增量追回：应用变更后按缓存状态定向失效，保留其余历史（v1.0.0）
                        ClientCableCache.applyDelta(lines, version);
                        CableUndoRedoManager.invalidateByState();
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
            root.add(CableProtocol.KEY_LINES, changesToJson(lines));
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
            if (lastOp != null)
            {
                JsonObject o = new JsonObject();
                o.addProperty(CableProtocol.KEY_PLAYER, lastOp.player());
                o.addProperty(CableProtocol.KEY_CODE, lastOp.code());
                o.addProperty(CableProtocol.KEY_VERSION, lastOp.version());
                o.addProperty(CableProtocol.KEY_OP, lastOp.op());
                o.add(CableProtocol.KEY_LINES, changesToJson(lastOp.lines()));
                root.add(CableProtocol.KEY_LAST, o);
            }
        }
        return root.toString();
    }

    private static JsonArray changesToJson(List<Change> changes)
    {
        JsonArray arr = new JsonArray();
        for (Change c : changes)
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
        return arr;
    }

    private static List<Change> changesFromJson(JsonElement element)
    {
        List<Change> changes = new ArrayList<>();
        if (element == null || !element.isJsonArray()) return changes;
        for (JsonElement e : element.getAsJsonArray())
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
        return changes;
    }

    private static CableMsgPacket fromJson(String json)
    {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String type = root.get(CableProtocol.KEY_TYPE).getAsString();
        if (CableProtocol.TYPE_REJECT.equals(type))
        {
            String rq = root.has(CableProtocol.KEY_REQUEST)
                ? root.get(CableProtocol.KEY_REQUEST).toString() : null;
            Concurrent last = null;
            if (root.has(CableProtocol.KEY_LAST))
            {
                JsonObject o = root.getAsJsonObject(CableProtocol.KEY_LAST);
                last = new Concurrent(
                    o.get(CableProtocol.KEY_PLAYER).getAsString(),
                    o.get(CableProtocol.KEY_CODE).getAsInt(),
                    o.get(CableProtocol.KEY_VERSION).getAsLong(),
                    o.get(CableProtocol.KEY_OP).getAsString(),
                    changesFromJson(o.get(CableProtocol.KEY_LINES)));
            }
            return new CableMsgPacket(type, 0, null, null, false, rq,
                root.get(CableProtocol.KEY_CODE).getAsInt(),
                root.has(CableProtocol.KEY_MESSAGE) ? root.get(CableProtocol.KEY_MESSAGE).getAsString() : null,
                last);
        }
        long v = root.get(CableProtocol.KEY_VERSION).getAsLong();
        String dm = root.get(CableProtocol.KEY_DIMENSION).getAsString();
        boolean ex = CableProtocol.TYPE_FULL.equals(type)
            && root.has(CableProtocol.KEY_EXPIRED) && root.get(CableProtocol.KEY_EXPIRED).getAsBoolean();
        List<Change> changes = root.has(CableProtocol.KEY_LINES)
            ? changesFromJson(root.get(CableProtocol.KEY_LINES)) : new ArrayList<>();
        return new CableMsgPacket(type, v, changes, dm, ex, null, 0, null, null);
    }
}
