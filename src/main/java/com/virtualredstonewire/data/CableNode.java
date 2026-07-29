package com.virtualredstonewire.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

public class CableNode
{
    private final BlockPos position;
    private final Map<BlockPos, Set<Direction>> toWho = new HashMap<>();
    private final Map<BlockPos, Set<Direction>> fromWho = new HashMap<>();

    public CableNode(BlockPos position)
    {
        this.position = position.immutable();
    }

    public BlockPos getPosition()
    {
        return position;
    }

    public boolean addOutgoing(BlockPos toPos, Direction toFace)
    {
        BlockPos key = toPos.immutable();
        Set<Direction> faces = toWho.computeIfAbsent(key, k -> new HashSet<>());
        return faces.add(toFace);
    }

    public boolean removeOutgoing(BlockPos toPos, Direction toFace)
    {
        BlockPos key = toPos.immutable();
        Set<Direction> faces = toWho.get(key);
        if (faces != null)
        {
            boolean removed = faces.remove(toFace);
            if (faces.isEmpty())
            {
                toWho.remove(key);
            }
            return removed;
        }
        return false;
    }

    public boolean hasOutgoing(BlockPos toPos)
    {
        Set<Direction> faces = toWho.get(toPos.immutable());
        return faces != null && !faces.isEmpty();
    }

    public boolean hasOutgoingFace(BlockPos toPos, Direction toFace)
    {
        Set<Direction> faces = toWho.get(toPos.immutable());
        return faces != null && faces.contains(toFace);
    }

    public Set<Direction> getOutgoingFaces(BlockPos toPos)
    {
        Set<Direction> faces = toWho.get(toPos.immutable());
        return faces != null ? new HashSet<>(faces) : Collections.emptySet();
    }

    public Set<Map.Entry<BlockPos, Direction>> getOutgoing()
    {
        Set<Map.Entry<BlockPos, Direction>> result = new HashSet<>();
        for (Map.Entry<BlockPos, Set<Direction>> entry : toWho.entrySet())
        {
            for (Direction face : entry.getValue())
            {
                result.add(Map.entry(entry.getKey(), face));
            }
        }
        return result;
    }

    public int getOutgoingCount()
    {
        int count = 0;
        for (Set<Direction> faces : toWho.values())
        {
            count += faces.size();
        }
        return count;
    }

    public void clearAllOutgoing()
    {
        toWho.clear();
    }

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

    public Set<Direction> clearIncomingFrom(BlockPos fromPos)
    {
        return fromWho.remove(fromPos.immutable());
    }

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

    public List<CableLink> toOutgoingCableLinks()
    {
        List<CableLink> result = new ArrayList<>();
        BlockPos self = this.position;
        for (Map.Entry<BlockPos, Set<Direction>> entry : toWho.entrySet())
        {
            for (Direction face : entry.getValue())
            {
                result.add(new CableLink(self, entry.getKey(), face));
            }
        }
        return result;
    }

    public List<CableLink> toOutgoingCableLinksTo(BlockPos toPos)
    {
        List<CableLink> result = new ArrayList<>();
        BlockPos self = this.position;
        Set<Direction> faces = toWho.get(toPos.immutable());
        if (faces != null)
        {
            for (Direction face : faces)
            {
                result.add(new CableLink(self, toPos, face));
            }
        }
        return result;
    }

    public void serializeToJson(JsonObject obj)
    {
        obj.addProperty("x", position.getX());
        obj.addProperty("y", position.getY());
        obj.addProperty("z", position.getZ());

        JsonArray toArr = new JsonArray();
        for (Map.Entry<BlockPos, Set<Direction>> entry : toWho.entrySet())
        {
            JsonObject edge = new JsonObject();
            edge.addProperty("tx", entry.getKey().getX());
            edge.addProperty("ty", entry.getKey().getY());
            edge.addProperty("tz", entry.getKey().getZ());
            JsonArray faces = new JsonArray();
            for (Direction d : entry.getValue())
            {
                faces.add(d.getName());
            }
            edge.add("faces", faces);
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
        if (!obj.has("x") || !obj.has("y") || !obj.has("z"))
        {
            com.virtualredstonewire.VirtualRedstoneWire.LOGGER.warn(
                "Skipping node: missing required coordinates (x/y/z)");
            return null;
        }
        try
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
                    if (!edge.has("tx") || !edge.has("ty") || !edge.has("tz"))
                    {
                        com.virtualredstonewire.VirtualRedstoneWire.LOGGER.warn(
                            "Skipping toWho edge at index {} for node [{},{},{}]: missing target coordinates",
                            i, x, y, z);
                        continue;
                    }
                    int tx = edge.get("tx").getAsInt();
                    int ty = edge.get("ty").getAsInt();
                    int tz = edge.get("tz").getAsInt();

                    JsonArray faces = getFacesArray(edge);
                    Set<Direction> dirs = new HashSet<>();
                    for (int j = 0; j < faces.size(); j++)
                    {
                        Direction d = Direction.byName(faces.get(j).getAsString());
                        if (d != null) dirs.add(d);
                    }
                    if (!dirs.isEmpty())
                    {
                        node.toWho.put(new BlockPos(tx, ty, tz), dirs);
                    }
                }
            }

            if (obj.has("fromWho"))
            {
                JsonArray fromArr = obj.getAsJsonArray("fromWho");
                for (int i = 0; i < fromArr.size(); i++)
                {
                    JsonObject edge = fromArr.get(i).getAsJsonObject();
                    if (!edge.has("fx") || !edge.has("fy") || !edge.has("fz"))
                    {
                        com.virtualredstonewire.VirtualRedstoneWire.LOGGER.warn(
                            "Skipping fromWho edge at index {} for node [{},{},{}]: missing source coordinates",
                            i, x, y, z);
                        continue;
                    }
                    int fx = edge.get("fx").getAsInt();
                    int fy = edge.get("fy").getAsInt();
                    int fz = edge.get("fz").getAsInt();

                    JsonArray faces = getFacesArray(edge);
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
        catch (Exception e)
        {
            com.virtualredstonewire.VirtualRedstoneWire.LOGGER.warn(
                "Failed to deserialize node: {}", e.getMessage());
            return null;
        }
    }

    private static JsonArray getFacesArray(JsonObject edge)
    {
        if (edge.has("faces") && edge.get("faces").isJsonArray())
        {
            return edge.getAsJsonArray("faces");
        }
        JsonArray arr = new JsonArray();
        if (edge.has("face") && !edge.get("face").isJsonNull())
        {
            try
            {
                arr.add(edge.get("face").getAsString());
            }
            catch (Exception e)
            {
                com.virtualredstonewire.VirtualRedstoneWire.LOGGER.warn(
                    "Failed to parse face field: {}", e.getMessage());
            }
        }
        return arr;
    }
}
