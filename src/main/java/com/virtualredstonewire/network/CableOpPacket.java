package com.virtualredstonewire.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.3.0 操作请求（客户端 -> 服务端）。
 * 载荷为 minified JSON 文本（缩写键名），由通道序列化传输。
 * op 为 add/del/pull；add/del 携带批量链路列表；pull 携带追回起点版本号。
 */
public class CableOpPacket
{
    /** 链路条目（坐标 + 输出面） */
    public record Link(int fx, int fy, int fz, int tx, int ty, int tz, String face)
    {
        public BlockPos from() { return new BlockPos(fx, fy, fz); }
        public BlockPos to() { return new BlockPos(tx, ty, tz); }
        public Direction faceDir() { return Direction.byName(face); }
    }

    public final String op;
    public final List<Link> links;
    public final String dimension;
    public final String player;
    public final long since;

    private CableOpPacket(String op, List<Link> links, String dimension, String player, long since)
    {
        this.op = op;
        this.links = links == null ? new ArrayList<>() : links;
        this.dimension = dimension;
        this.player = player;
        this.since = since;
    }

    public static CableOpPacket add(List<Link> links, ResourceKey<Level> dim, String player)
    {
        return new CableOpPacket(CableProtocol.OP_ADD, links, dim.location().toString(), player, 0);
    }

    public static CableOpPacket del(List<Link> links, ResourceKey<Level> dim, String player)
    {
        return new CableOpPacket(CableProtocol.OP_DEL, links, dim.location().toString(), player, 0);
    }

    public static CableOpPacket pull(long since, ResourceKey<Level> dim, String player)
    {
        return new CableOpPacket(CableProtocol.OP_PULL, new ArrayList<>(), dim.location().toString(), player, since);
    }

    public boolean isPull() { return CableProtocol.OP_PULL.equals(op); }

    public CableOpPacket(FriendlyByteBuf buf)
    {
        this(fromJson(buf.readUtf(32767)));
    }

    private CableOpPacket(CableOpPacket other)
    {
        this(other.op, other.links, other.dimension, other.player, other.since);
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeUtf(toJson());
    }

    public String toJson()
    {
        JsonObject root = new JsonObject();
        root.addProperty(CableProtocol.KEY_OP, op);
        JsonArray arr = new JsonArray();
        for (Link l : links)
        {
            JsonObject o = new JsonObject();
            o.add(CableProtocol.KEY_FROM, coords(l.fx(), l.fy(), l.fz()));
            o.add(CableProtocol.KEY_TO, coords(l.tx(), l.ty(), l.tz()));
            o.addProperty(CableProtocol.KEY_FACE, l.face());
            arr.add(o);
        }
        root.add(CableProtocol.KEY_LINES, arr);
        root.addProperty(CableProtocol.KEY_DIMENSION, dimension);
        root.addProperty(CableProtocol.KEY_PLAYER, player);
        if (isPull())
        {
            root.addProperty(CableProtocol.KEY_SINCE, since);
        }
        return root.toString();
    }

    private static JsonArray coords(int x, int y, int z)
    {
        JsonArray arr = new JsonArray();
        arr.add(x);
        arr.add(y);
        arr.add(z);
        return arr;
    }

    private static CableOpPacket fromJson(String json)
    {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String op = root.get(CableProtocol.KEY_OP).getAsString();
        String dm = root.get(CableProtocol.KEY_DIMENSION).getAsString();
        String pl = root.get(CableProtocol.KEY_PLAYER).getAsString();
        List<Link> links = new ArrayList<>();
        if (root.has(CableProtocol.KEY_LINES))
        {
            for (JsonElement e : root.getAsJsonArray(CableProtocol.KEY_LINES))
            {
                JsonObject o = e.getAsJsonObject();
                JsonArray fr = o.getAsJsonArray(CableProtocol.KEY_FROM);
                JsonArray to = o.getAsJsonArray(CableProtocol.KEY_TO);
                links.add(new Link(
                    fr.get(0).getAsInt(), fr.get(1).getAsInt(), fr.get(2).getAsInt(),
                    to.get(0).getAsInt(), to.get(1).getAsInt(), to.get(2).getAsInt(),
                    o.get(CableProtocol.KEY_FACE).getAsString()));
            }
        }
        long sc = root.has(CableProtocol.KEY_SINCE) ? root.get(CableProtocol.KEY_SINCE).getAsLong() : 0;
        return new CableOpPacket(op, links, dm, pl, sc);
    }
}
