package com.virtualredstonewire.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

public class CableNetwork
{
    private final Map<String, CableLink> links = new HashMap<>();

    // 正向邻接表：from BlockPos -> Set<(to BlockPos, Direction)>
    private final Map<BlockPos, Set<Map.Entry<BlockPos, Direction>>> forwardAdj = new HashMap<>();

    // 反向邻接表：(to BlockPos, Direction) -> Set<from BlockPos>
    private final Map<Map.Entry<BlockPos, Direction>, Set<BlockPos>> reverseAdj = new HashMap<>();

    /**
     * 添加或删除链路（开关式）
     * @return true=新建, false=删除
     */
    public boolean toggleLink(BlockPos from, BlockPos to, Direction toFace)
    {
        String id = CableLink.generateId(from, to, toFace);

        if (links.containsKey(id))
        {
            removeLink(id);
            return false;
        }
        else
        {
            CableLink link = new CableLink(from, to, toFace);
            links.put(id, link);

            Map.Entry<BlockPos, Direction> target = Map.entry(to.immutable(), toFace);
            forwardAdj.computeIfAbsent(from.immutable(), k -> new HashSet<>()).add(target);

            reverseAdj.computeIfAbsent(target, k -> new HashSet<>()).add(from.immutable());

            return true;
        }
    }

    /**
     * 删除指定链路
     */
    public void removeLink(String id)
    {
        CableLink link = links.remove(id);
        if (link == null) return;

        Map.Entry<BlockPos, Direction> target = Map.entry(link.getTo(), link.getToFace());

        Set<Map.Entry<BlockPos, Direction>> outEdges = forwardAdj.get(link.getFrom());
        if (outEdges != null)
        {
            outEdges.remove(target);
            if (outEdges.isEmpty()) forwardAdj.remove(link.getFrom());
        }

        Set<BlockPos> inNodes = reverseAdj.get(target);
        if (inNodes != null)
        {
            inNodes.remove(link.getFrom());
            if (inNodes.isEmpty()) reverseAdj.remove(target);
        }
    }

    /**
     * 删除与指定坐标相连的所有链路（剪刀操作）
     */
    public int removeAllLinksAt(BlockPos pos)
    {
        int count = 0;
        BlockPos immutablePos = pos.immutable();

        // 删除所有以 pos 为输入的链路
        Set<Map.Entry<BlockPos, Direction>> outEdges = forwardAdj.get(immutablePos);
        if (outEdges != null)
        {
            List<Map.Entry<BlockPos, Direction>> edges = new ArrayList<>(outEdges);
            for (Map.Entry<BlockPos, Direction> edge : edges)
            {
                String id = CableLink.generateId(immutablePos, edge.getKey(), edge.getValue());
                removeLink(id);
                count++;
            }
        }

        // 删除所有以 pos 为输出的链路
        for (Direction face : Direction.values())
        {
            Map.Entry<BlockPos, Direction> key = Map.entry(immutablePos, face);
            Set<BlockPos> inNodes = reverseAdj.get(key);
            if (inNodes != null)
            {
                List<BlockPos> nodes = new ArrayList<>(inNodes);
                for (BlockPos from : nodes)
                {
                    String id = CableLink.generateId(from, immutablePos, face);
                    removeLink(id);
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * 获取指定输出端的所有输入坐标
     */
    public Set<BlockPos> getInputsForOutput(BlockPos to, Direction toFace)
    {
        return reverseAdj.getOrDefault(Map.entry(to.immutable(), toFace), Collections.emptySet());
    }

    /**
     * 获取指定输入端的所有输出目标
     */
    public Set<Map.Entry<BlockPos, Direction>> getOutputsForInput(BlockPos from)
    {
        return forwardAdj.getOrDefault(from.immutable(), Collections.emptySet());
    }

    /**
     * 检查指定输出端是否有链路
     */
    public boolean hasOutputAt(BlockPos to, Direction toFace)
    {
        return reverseAdj.containsKey(Map.entry(to.immutable(), toFace));
    }

    /**
     * 获取所有输入坐标集合
     */
    public Set<BlockPos> getInputPositions()
    {
        return forwardAdj.keySet();
    }

    /**
     * 获取所有链路
     */
    public Collection<CableLink> getAllLinks()
    {
        return links.values();
    }

    /**
     * 获取链路数量
     */
    public int getLinkCount()
    {
        return links.size();
    }

    /**
     * 查询指定坐标的出边列表（GUI用）
     */
    public List<String> queryOutgoing(BlockPos pos)
    {
        List<String> result = new ArrayList<>();
        Set<Map.Entry<BlockPos, Direction>> edges = forwardAdj.get(pos.immutable());
        if (edges != null)
        {
            for (Map.Entry<BlockPos, Direction> edge : edges)
            {
                BlockPos to = edge.getKey();
                Direction face = edge.getValue();
                result.add("-> [" + to.getX() + "," + to.getY() + "," + to.getZ()
                    + "] " + face.getName());
            }
        }
        return result;
    }

    /**
     * 查询指定坐标的入边列表（GUI用）
     */
    public List<String> queryIncoming(BlockPos pos)
    {
        List<String> result = new ArrayList<>();
        BlockPos immutablePos = pos.immutable();
        for (Direction face : Direction.values())
        {
            Set<BlockPos> froms = reverseAdj.get(Map.entry(immutablePos, face));
            if (froms != null)
            {
                for (BlockPos from : froms)
                {
                    result.add("<- [" + from.getX() + "," + from.getY() + "," + from.getZ()
                        + "] " + face.getName());
                }
            }
        }
        return result;
    }

    /**
     * 序列化为JSON
     */
    public JsonObject toJson()
    {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray linksArray = new JsonArray();
        for (CableLink link : links.values())
        {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", link.getId());
            obj.addProperty("fromX", link.getFrom().getX());
            obj.addProperty("fromY", link.getFrom().getY());
            obj.addProperty("fromZ", link.getFrom().getZ());
            obj.addProperty("toX", link.getTo().getX());
            obj.addProperty("toY", link.getTo().getY());
            obj.addProperty("toZ", link.getTo().getZ());
            obj.addProperty("face", link.getToFace().getName());
            linksArray.add(obj);
        }
        root.add("links", linksArray);
        return root;
    }

    /**
     * 从JSON反序列化
     */
    public static CableNetwork fromJson(JsonObject json)
    {
        CableNetwork network = new CableNetwork();
        JsonArray linksArray = json.getAsJsonArray("links");
        if (linksArray != null)
        {
            for (JsonElement elem : linksArray)
            {
                JsonObject obj = elem.getAsJsonObject();
                String id = obj.get("id").getAsString();
                BlockPos from = new BlockPos(
                    obj.get("fromX").getAsInt(),
                    obj.get("fromY").getAsInt(),
                    obj.get("fromZ").getAsInt());
                BlockPos to = new BlockPos(
                    obj.get("toX").getAsInt(),
                    obj.get("toY").getAsInt(),
                    obj.get("toZ").getAsInt());
                Direction face = Direction.byName(obj.get("face").getAsString());
                if (face != null)
                {
                    CableLink link = new CableLink(id, from, to, face);
                    network.links.put(id, link);
                    Map.Entry<BlockPos, Direction> target = Map.entry(to.immutable(), face);
                    network.forwardAdj.computeIfAbsent(from.immutable(), k -> new HashSet<>()).add(target);
                    network.reverseAdj.computeIfAbsent(target, k -> new HashSet<>()).add(from.immutable());
                }
            }
        }
        return network;
    }
}
