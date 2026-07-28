package com.virtualredstonewire.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

public class CableNode
{
    private final BlockPos position;
    private final Map<BlockPos, Direction> toWho = new HashMap<>();
    private final Map<BlockPos, Set<Direction>> fromWho = new HashMap<>();

    public CableNode(BlockPos position)
    {
        this.position = position.immutable();
    }

    public BlockPos getPosition()
    {
        return position;
    }

    /* toWho: 本结点作为输入时，连到的(目标位, 面) */
    public boolean addOutgoing(BlockPos toPos, Direction toFace)
    {
        BlockPos key = toPos.immutable();
        if (toWho.containsKey(key) && toWho.get(key) == toFace)
        {
            return false;
        }
        toWho.put(key, toFace);
        return true;
    }

    public boolean removeOutgoing(BlockPos toPos)
    {
        return toWho.remove(toPos.immutable()) != null;
    }

    public boolean hasOutgoing(BlockPos toPos)
    {
        return toWho.containsKey(toPos.immutable());
    }

    public Direction getOutgoingFace(BlockPos toPos)
    {
        return toWho.get(toPos.immutable());
    }

    public Set<Map.Entry<BlockPos, Direction>> getOutgoing()
    {
        return toWho.entrySet();
    }

    public int getOutgoingCount()
    {
        return toWho.size();
    }

    /* fromWho: 本结点作为输出时，哪些(输入位)连到哪些面 */
    public void addIncoming(BlockPos fromPos, Direction toFace)
    {
        fromWho.computeIfAbsent(fromPos.immutable(), k -> new HashSet<>()).add(toFace);
    }

    public boolean removeIncoming(BlockPos fromPos, Direction toFace)
    {
        BlockPos key = fromPos.immutable();
        Set<Direction> faces = fromWho.get(key);
        if (faces != null)
        {
            faces.remove(toFace);
            if (faces.isEmpty())
            {
                fromWho.remove(key);
            }
            return true;
        }
        return false;
    }

    public boolean hasIncoming(BlockPos fromPos)
    {
        return fromWho.containsKey(fromPos.immutable());
    }

    public Set<BlockPos> getIncomingForFace(Direction toFace)
    {
        Set<BlockPos> result = new HashSet<>();
        for (Map.Entry<BlockPos, Set<Direction>> entry : fromWho.entrySet())
        {
            if (entry.getValue().contains(toFace))
            {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public Set<BlockPos> getAllIncoming()
    {
        return fromWho.keySet();
    }

    public Map<BlockPos, Set<Direction>> getAllIncomingMap()
    {
        return fromWho;
    }

    public boolean hasAnyInputOnFace(Direction toFace)
    {
        for (Set<Direction> faces : fromWho.values())
        {
            if (faces.contains(toFace)) return true;
        }
        return false;
    }

    public int getIncomingCount()
    {
        return fromWho.size();
    }

    /* 从fromWho清除所有以fromPos为输入源的条目，并返回受影响的面集合 */
    public Set<Direction> clearIncomingFrom(BlockPos fromPos)
    {
        return fromWho.remove(fromPos.immutable());
    }

    /* 遍历 fromWho 生成 CableLink 列表 */
    public List<CableLink> toCableLinks()
    {
        List<CableLink> result = new ArrayList<>();
        BlockPos self = this.position;
        for (Map.Entry<BlockPos, Set<Direction>> entry : fromWho.entrySet())
        {
            BlockPos from = entry.getKey();
            for (Direction face : entry.getValue())
            {
                result.add(new CableLink(from, self, face));
            }
        }
        return result;
    }

    /* 遍历 toWho 生成以本结点为from的CableLink列表 */
    public List<CableLink> toOutgoingCableLinks()
    {
        List<CableLink> result = new ArrayList<>();
        BlockPos self = this.position;
        for (Map.Entry<BlockPos, Direction> entry : toWho.entrySet())
        {
            result.add(new CableLink(self, entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public void serializeToJson(JsonObject obj)
    {
        obj.addProperty("x", position.getX());
        obj.addProperty("y", position.getY());
        obj.addProperty("z", position.getZ());

        JsonArray toArr = new JsonArray();
        for (Map.Entry<BlockPos, Direction> entry : toWho.entrySet())
        {
            JsonObject edge = new JsonObject();
            edge.addProperty("tx", entry.getKey().getX());
            edge.addProperty("ty", entry.getKey().getY());
            edge.addProperty("tz", entry.getKey().getZ());
            edge.addProperty("face", entry.getValue().getName());
            toArr.add(edge);
        }
        obj.add("toWho", toArr);

        JsonArray fromArr = new JsonArray();
        for (Map.Entry<BlockPos, Set<Direction>> entry : fromWho.entrySet())
        {
            JsonObject edge = new JsonObject();
            edge.addProperty("fx", entry.getKey().getX());
            edge.addProperty("fy", entry.getKey().getY());
            edge.addProperty("fz", entry.getKey().getZ());
            JsonArray faces = new JsonArray();
            for (Direction d : entry.getValue())
            {
                faces.add(d.getName());
            }
            edge.add("faces", faces);
            fromArr.add(edge);
        }
        obj.add("fromWho", fromArr);
    }

    public static CableNode deserializeFromJson(JsonObject obj)
    {
        int x = obj.get("x").getAsInt();
        int y = obj.get("y").getAsInt();
        int z = obj.get("z").getAsInt();
        CableNode node = new CableNode(new BlockPos(x, y, z));

        if (obj.has("toWho"))
        {
            JsonArray toArr = obj.getAsJsonArray("toWho");
            for (int i = 0; i < toArr.size(); i++)
            {
                JsonObject edge = toArr.get(i).getAsJsonObject();
                int tx = edge.get("tx").getAsInt();
                int ty = edge.get("ty").getAsInt();
                int tz = edge.get("tz").getAsInt();
                Direction face = Direction.byName(edge.get("face").getAsString());
                if (face != null)
                {
                    node.toWho.put(new BlockPos(tx, ty, tz), face);
                }
            }
        }

        if (obj.has("fromWho"))
        {
            JsonArray fromArr = obj.getAsJsonArray("fromWho");
            for (int i = 0; i < fromArr.size(); i++)
            {
                JsonObject edge = fromArr.get(i).getAsJsonObject();
                int fx = edge.get("fx").getAsInt();
                int fy = edge.get("fy").getAsInt();
                int fz = edge.get("fz").getAsInt();
                JsonArray faces = edge.getAsJsonArray("faces");
                Set<Direction> dirs = new HashSet<>();
                for (int j = 0; j < faces.size(); j++)
                {
                    Direction d = Direction.byName(faces.get(j).getAsString());
                    if (d != null) dirs.add(d);
                }
                if (!dirs.isEmpty())
                {
                    node.fromWho.put(new BlockPos(fx, fy, fz), dirs);
                }
            }
        }

        return node;
    }
}
