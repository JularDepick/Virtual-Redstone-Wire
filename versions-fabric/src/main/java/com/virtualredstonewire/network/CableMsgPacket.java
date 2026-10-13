package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.virtualredstonewire.client.CableClientQueue;
import com.virtualredstonewire.client.CableFullSyncBuffer;
import com.virtualredstonewire.client.CableUndoRedoManager;
import com.virtualredstonewire.client.ClientCableCache;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息（服务端 -> 客户端）。
 * 客户端按报文的 dm 校验维度，增量追回不再清空撤销/重做双栈，改为按缓存状态定向失效。
 * 修复（维度隔离失效）：拒绝响应同样携带 dm 并按维度校验；缓存残留其他维度内容时
 */
public class CableMsgPacket implements CablePayload
{
    public static final ResourceLocation ID =
        new ResourceLocation(VirtualRedstoneWire.MOD_ID, "msg");

    @Override
    public ResourceLocation id()
    {
        return ID;
    }

    /** 变更条目（带操作类型） */
    public record Change(String op, int fx, int fy, int fz, int tx, int ty, int tz, String face) {}

    /** 其他客户端最近一次成功操作的摘要（拒绝响应 lx 字段） */
    public record Concurrent(String player, long version, String op, List<Change> lines) {}

    public final String type;
    public final long version;
    public final List<Change> lines;
    public final String dimension;
    public final boolean expired;
    public final String requestJson;
    public final int code;
    public final String message;
    public final Concurrent lastOp;
    /** 全量与追回响应回显的请求追回起点（0 为全量，大于 0 为增量追回） */
    public final long since;
    /** 全量/追回响应分片序号与总分片数（未分片时为 0 与 1） */
    public final int chunkIndex;
    public final int chunkCount;
    /** 增量广播本批请求者名单（空表示未知，客户端按内容结算） */
    public final List<String> requesters;

    private CableMsgPacket(String type, long version, List<Change> lines, String dimension,
                           boolean expired, String requestJson, int code, String message,
                           Concurrent lastOp, long since, int chunkIndex, int chunkCount,
                           List<String> requesters)
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
        this.since = since;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount < 1 ? 1 : chunkCount;
        this.requesters = requesters == null ? new ArrayList<>() : requesters;
    }

    /** 增量广播携带本批请求者名单，客户端据此判定是否为本机请求的确认 */
    public static CableMsgPacket delta(long version, List<Change> lines, String dimension,
                                       List<String> requesters)
    {
        return new CableMsgPacket(CableProtocol.TYPE_DELTA, version, lines, dimension,
            false, null, 0, null, null, 0, 0, 1, requesters);
    }

    /** since 回显请求的追回起点，供客户端判定全量（0）与增量追回（大于 0） */
    public static CableMsgPacket full(long version, List<Change> lines, String dimension,
                                      boolean expired, long since)
    {
        return fullChunk(version, lines, dimension, expired, since, 0, 1);
    }

    /** 分片版全量/追回响应（ci 从 0 起，cc 为总片数） */
    public static CableMsgPacket fullChunk(long version, List<Change> lines, String dimension,
                                           boolean expired, long since, int chunkIndex, int chunkCount)
    {
        return new CableMsgPacket(CableProtocol.TYPE_FULL, version, lines, dimension,
            expired, null, 0, null, null, since, chunkIndex, chunkCount, null);
    }

    /** 拒绝响应携带该批请求所属维度（客户端按 dm 校验，避免跨维度顶掉在途请求） */
    public static CableMsgPacket reject(String requestJson, int code, String message, String dimension)
    {
        return reject(requestJson, code, message, null, dimension);
    }

    /** 拒绝响应可附带其他客户端最近一次成功操作的摘要 */
    public static CableMsgPacket reject(String requestJson, int code, String message, Concurrent lastOp,
                                        String dimension)
    {
        return new CableMsgPacket(CableProtocol.TYPE_REJECT, 0, null, dimension,
            false, requestJson, code, message, lastOp, 0, 0, 1, null);
    }

    /**
     * 由服务端最近成功操作摘要构造 lx 载荷；
     */
    public static Concurrent concurrentOf(CableNetworkManager.LastOperation last, String requesterName)
    {
        if (last == null || last.participants().contains(requesterName)) return null;
        List<Change> changes = new ArrayList<>();
        for (CableNetworkManager.LastOperation.LinkEntry e : last.links())
        {
            if (e.from() == null || e.to() == null || e.face() == null) continue;
            changes.add(new Change(last.op(), e.from().getX(), e.from().getY(), e.from().getZ(),
                e.to().getX(), e.to().getY(), e.to().getZ(), e.face().getName()));
        }
        if (changes.isEmpty()) return null;
        return new Concurrent(last.player(), last.version(), last.op(), changes);
    }

    /** lx 携带的链路是否与给定链路集合相交（用于失败原因细分） */
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
            other.expired, other.requestJson, other.code, other.message, other.lastOp, other.since,
            other.chunkIndex, other.chunkCount, other.requesters);
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeUtf(toJson());
    }

    public boolean isDelta() { return CableProtocol.TYPE_DELTA.equals(type); }
    public boolean isFull() { return CableProtocol.TYPE_FULL.equals(type); }
    public boolean isReject() { return CableProtocol.TYPE_REJECT.equals(type); }

    /** 客户端接收器：只在客户端注册，侧判定与回主线程包装都不再需要 */
    public void handle(net.minecraft.client.Minecraft client)
    {
        if (isDelta())
        {
            if (!isCurrentDimension(dimension))
            {
                /* 非当前维度的广播只作废在途请求并推进队列，不改动缓存*/
                CableClientQueue.onForeignDimension();
            }
            else if (cacheHoldsForeignDimension())
            {
                /* 缓存内容仍属于其他维度（换维度守卫尚未跑到）：整体作废并强制全量重拉，*/
                /* 不能把当前维度的增量并入旧维度内容*/
                ClientCableCache.clear();
                CableClientQueue.onDimensionChanged();
                CableClientQueue.requestFull(true);
            }
            else if (version > ClientCableCache.getVersion())
            {
                ClientCableCache.applyDelta(dimension, lines, version);
                CableClientQueue.onDeltaReceived(lines, requesters);
            }
            else if (version < ClientCableCache.getVersion())
            {
                CableClientQueue.onVersionRollback();
            }
            else
            {
                /* 版本号相等属重复广播，不写缓存，但必须按内容结算在途请求，*/
                /* 否则 sendNext 会因在途未解除而永久停摆*/
                CableClientQueue.onDeltaReceived(lines, requesters);
            }
        }
        else if (isFull())
        {
            if (!isCurrentDimension(dimension))
            {
                /* 非当前维度的响应不改动缓存与历史，仅复位在途并推进队列*/
                CableFullSyncBuffer.reset();
                CableClientQueue.onForeignDimension();
                return;
            }
            /* 大响应按 ci/cc 分片，收齐后再应用；未收齐直接返回*/
            List<Change> payload = lines;
            if (chunkCount > 1)
            {
                payload = CableFullSyncBuffer.accept(dimension, version, expired, since,
                    chunkIndex, chunkCount, lines);
                if (payload == null) return;
            }
            else
            {
                CableFullSyncBuffer.reset();
            }
            /* 按报文回显的 sc 判定全量与增量追回，不再依赖客户端在途请求状态*/
            boolean fullPull = since == 0;
            /* 全量与过期标记：缓存整体重建或已失效，前提全面失效，清空双栈*/
            if (fullPull || expired)
            {
                CableUndoRedoManager.clearHistory();
            }
            if (expired)
            {
                /* 过期响应已使本次追回请求终结：先复位在途请求，再强制发起全量请求，*/
                /* 否则新请求会因在途未复位而滞留队列，需等下一次广播才发出；*/
                /* 强制通道绕开节流，过期意味着缓存不可信，不能因 5 秒冷却丢弃*/
                CableClientQueue.onFullReceived();
                CableClientQueue.requestFull(true);
            }
            else if (fullPull)
            {
                ClientCableCache.applyFull(dimension, payload, version);
                CableClientQueue.onFullReceived();
            }
            else
            {
                /* 增量追回：应用变更后按缓存状态定向失效，保留其余历史*/
                ClientCableCache.applyDelta(dimension, payload, version);
                CableUndoRedoManager.invalidateByState();
                CableClientQueue.onFullReceived();
            }
        }
        else if (isReject())
        {
            /* 拒绝响应按报文 dm 校验维度，非当前维度只复位在途并推进队列，*/
            /* 避免旧维度的拒绝顶掉当前维度的在途请求；dm 缺失时按当前维度处理*/
            if (dimension != null && !isCurrentDimension(dimension))
            {
                CableClientQueue.onForeignDimension();
            }
            else
            {
                CableClientQueue.onRejectReceived(this);
            }
        }
    }

    /** 报文的维度是否与客户端当前维度一致（未进入世界时按不一致处理） */
    private static boolean isCurrentDimension(String dimension)
    {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc.level != null && mc.level.dimension().location().toString().equals(dimension);
    }

    /**
     * 客户端缓存是否残留其他维度的内容（缓存所属维度非空且与当前维度不一致）。
     * 缓存为空（从未写入或已作废）不算残留。
     */
    private static boolean cacheHoldsForeignDimension()
    {
        String cached = ClientCableCache.getDimension();
        return cached != null && !isCurrentDimension(cached);
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
            if (isDelta() && !requesters.isEmpty())
            {
                /* 增量广播附带本批请求者名单，客户端据此判定是否为本机请求的确认*/
                JsonArray pl = new JsonArray();
                for (String name : requesters) pl.add(name);
                root.add(CableProtocol.KEY_PLAYER, pl);
            }
            if (isFull())
            {
                root.addProperty(CableProtocol.KEY_EXPIRED, expired);
                root.addProperty(CableProtocol.KEY_SINCE, since);
                if (chunkCount > 1)
                {
                    /* 仅在分片时写入 ci/cc，单片响应与旧形状保持一致*/
                    root.addProperty(CableProtocol.KEY_CHUNK_INDEX, chunkIndex);
                    root.addProperty(CableProtocol.KEY_CHUNK_COUNT, chunkCount);
                }
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
            /* 拒绝响应携带该批请求所属维度，客户端据此校验（缺失时客户端按当前维度处理）*/
            if (dimension != null)
            {
                root.addProperty(CableProtocol.KEY_DIMENSION, dimension);
            }
            if (lastOp != null)
            {
                JsonObject o = new JsonObject();
                o.addProperty(CableProtocol.KEY_PLAYER, lastOp.player());
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
            /* 条目残缺或输出面非法时跳过该条，避免解码路径抛异常或写入 null 输出面*/
            try
            {
                JsonObject o = e.getAsJsonObject();
                JsonArray fr = o.getAsJsonArray(CableProtocol.KEY_FROM);
                JsonArray to = o.getAsJsonArray(CableProtocol.KEY_TO);
                if (fr == null || to == null || fr.size() < 3 || to.size() < 3) continue;
                if (!o.has(CableProtocol.KEY_OP)) continue;
                String face = o.has(CableProtocol.KEY_FACE)
                    ? o.get(CableProtocol.KEY_FACE).getAsString() : null;
                if (face == null || net.minecraft.core.Direction.byName(face) == null) continue;
                changes.add(new Change(
                    o.get(CableProtocol.KEY_OP).getAsString(),
                    fr.get(0).getAsInt(), fr.get(1).getAsInt(), fr.get(2).getAsInt(),
                    to.get(0).getAsInt(), to.get(1).getAsInt(), to.get(2).getAsInt(),
                    face));
            }
            catch (RuntimeException ignored)
            {
                /* 单条残缺不影响其余条目*/
            }
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
            /* 摘要字段残缺时按无摘要处理，避免解码路径抛异常*/
            if (root.has(CableProtocol.KEY_LAST))
            {
                JsonObject o = root.getAsJsonObject(CableProtocol.KEY_LAST);
                if (o.has(CableProtocol.KEY_PLAYER) && o.has(CableProtocol.KEY_VERSION)
                    && o.has(CableProtocol.KEY_OP))
                {
                    last = new Concurrent(
                        o.get(CableProtocol.KEY_PLAYER).getAsString(),
                        o.get(CableProtocol.KEY_VERSION).getAsLong(),
                        o.get(CableProtocol.KEY_OP).getAsString(),
                        changesFromJson(o.get(CableProtocol.KEY_LINES)));
                }
            }
            /* 拒绝响应可携带维度；缺失（异常/旧数据）时按 null 处理，客户端不做维度校验*/
            String dm = root.has(CableProtocol.KEY_DIMENSION)
                && !root.get(CableProtocol.KEY_DIMENSION).isJsonNull()
                ? root.get(CableProtocol.KEY_DIMENSION).getAsString() : null;
            return new CableMsgPacket(type, 0, null, dm, false, rq,
                root.get(CableProtocol.KEY_CODE).getAsInt(),
                root.has(CableProtocol.KEY_MESSAGE) ? root.get(CableProtocol.KEY_MESSAGE).getAsString() : null,
                last, 0, 0, 1, null);
        }
        long v = root.get(CableProtocol.KEY_VERSION).getAsLong();
        String dm = root.get(CableProtocol.KEY_DIMENSION).getAsString();
        boolean isFullType = CableProtocol.TYPE_FULL.equals(type);
        boolean ex = isFullType
            && root.has(CableProtocol.KEY_EXPIRED) && root.get(CableProtocol.KEY_EXPIRED).getAsBoolean();
        long sc = isFullType && root.has(CableProtocol.KEY_SINCE)
            ? root.get(CableProtocol.KEY_SINCE).getAsLong() : 0;
        /* 分片字段缺失时按单片处理（ci=0, cc=1），旧形状报文无需改动*/
        int ci = root.has(CableProtocol.KEY_CHUNK_INDEX)
            ? root.get(CableProtocol.KEY_CHUNK_INDEX).getAsInt() : 0;
        int cc = root.has(CableProtocol.KEY_CHUNK_COUNT)
            ? root.get(CableProtocol.KEY_CHUNK_COUNT).getAsInt() : 1;
        List<String> requesters = new ArrayList<>();
        if (root.has(CableProtocol.KEY_PLAYER))
        {
            JsonElement pl = root.get(CableProtocol.KEY_PLAYER);
            if (pl.isJsonArray())
            {
                for (JsonElement e : pl.getAsJsonArray())
                {
                    if (!e.isJsonNull() && e.isJsonPrimitive()) requesters.add(e.getAsString());
                }
            }
            else if (pl.isJsonPrimitive())
            {
                requesters.add(pl.getAsString());
            }
        }
        List<Change> changes = root.has(CableProtocol.KEY_LINES)
            ? changesFromJson(root.get(CableProtocol.KEY_LINES)) : new ArrayList<>();
        return new CableMsgPacket(type, v, changes, dm, ex, null, 0, null, null, sc, ci, cc, requesters);
    }
}
