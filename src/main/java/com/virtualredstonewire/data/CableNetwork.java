package com.virtualredstonewire.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

public class CableNetwork
{
    private final Map<BlockPos, CableNode> nodes = new HashMap<>();

    public CableNode getOrCreateNode(BlockPos pos)
    {
        return nodes.computeIfAbsent(pos.immutable(), CableNode::new);
    }

    public CableNode getNode(BlockPos pos)
    {
        return nodes.get(pos.immutable());
    }

    public void removeNode(BlockPos pos)
    {
        nodes.remove(pos.immutable());
    }

    public boolean toggleLink(BlockPos from, BlockPos to, Direction toFace)
    {
        CableNode inputNode = getOrCreateNode(from);
        CableNode outputNode = getOrCreateNode(to);

        if (inputNode.hasOutgoingFace(to, toFace))
        {
            inputNode.removeOutgoing(to, toFace);
            outputNode.removeIncoming(from, toFace);

            if (outputNode.getIncomingCount() == 0 && inputNode.getIncomingCount() == 0)
            {
                removeNode(to);
            }
            if (inputNode.getOutgoingCount() == 0
                && inputNode.getIncomingCount() == 0)
            {
                removeNode(from);
            }

            return false;
        }
        else
        {
            inputNode.addOutgoing(to, toFace);
            outputNode.addIncoming(from, toFace);

            return true;
        }
    }

    public int removeLinksFrom(BlockPos from)
    {
        CableNode node = getNode(from);
        if (node == null) return 0;

        int count = 0;
        List<Map.Entry<BlockPos, Direction>> outgoing = new ArrayList<>(node.getOutgoing());
        for (Map.Entry<BlockPos, Direction> edge : outgoing)
        {
            BlockPos toPos = edge.getKey();
            Direction toFace = edge.getValue();
            CableNode targetNode = getNode(toPos);
            if (targetNode != null)
            {
                targetNode.removeIncoming(from, toFace);
                if (targetNode.getIncomingCount() == 0)
                {
                    removeNode(toPos);
                }
            }
            count++;
        }
        node.clearAllOutgoing();

        if (node.getIncomingCount() == 0)
        {
            removeNode(from);
        }

        return count;
    }

    public Set<BlockPos> getInputsForOutput(BlockPos to, Direction toFace)
    {
        CableNode node = getNode(to);
        if (node == null) return Collections.emptySet();
        return node.getIncomingForFace(toFace);
    }

    public Set<Map.Entry<BlockPos, Direction>> getOutputsForInput(BlockPos from)
    {
        CableNode node = getNode(from);
        if (node == null) return Collections.emptySet();
        return node.getOutgoing();
    }

    public boolean hasOutputAt(BlockPos to, Direction toFace)
    {
        CableNode node = getNode(to);
        if (node == null) return false;
        return node.hasAnyInputOnFace(toFace);
    }

    public int getSignalAt(BlockPos pos, Direction direction, net.minecraft.world.level.Level level)
    {
        CableNode node = getNode(pos);
        if (node == null) return 0;
        Set<BlockPos> inputs = node.getIncomingForFace(direction);
        if (inputs.isEmpty()) return 0;
        int maxPower = 0;
        for (BlockPos inPos : inputs)
        {
            for (Direction dir : Direction.values())
            {
                int p = level.getBlockState(inPos).getSignal(level, inPos, dir);
                if (p > maxPower) maxPower = p;
            }
        }
        return maxPower;
    }

    public Set<BlockPos> getInputPositions()
    {
        Set<BlockPos> result = new HashSet<>();
        for (CableNode node : nodes.values())
        {
            if (node.getOutgoingCount() > 0)
            {
                result.add(node.getPosition());
            }
        }
        return result;
    }

    public Collection<CableLink> getAllLinks()
    {
        List<CableLink> result = new ArrayList<>();
        for (CableNode node : nodes.values())
        {
            result.addAll(node.toOutgoingCableLinks());
        }
        return result;
    }

    public int getLinkCount()
    {
        int count = 0;
        for (CableNode node : nodes.values())
        {
            count += node.getOutgoingCount();
        }
        return count;
    }

    public List<String> queryOutgoing(BlockPos pos)
    {
        List<String> result = new ArrayList<>();
        CableNode node = getNode(pos);
        if (node != null)
        {
            for (Map.Entry<BlockPos, Direction> edge : node.getOutgoing())
            {
                BlockPos to = edge.getKey();
                result.add("-> [" + to.getX() + "," + to.getY() + "," + to.getZ()
                    + "] " + edge.getValue().getName());
            }
        }
        return result;
    }

    public List<String> queryIncoming(BlockPos pos)
    {
        List<String> result = new ArrayList<>();
        CableNode node = getNode(pos);
        if (node != null)
        {
            for (Map.Entry<BlockPos, Set<Direction>> entry : node.getAllIncomingMap().entrySet())
            {
                BlockPos from = entry.getKey();
                for (Direction face : entry.getValue())
                {
                    result.add("<- [" + from.getX() + "," + from.getY() + "," + from.getZ()
                        + "] " + face.getName());
                }
            }
        }
        return result;
    }

    public JsonObject toJson()
    {
        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        JsonArray nodesArray = new JsonArray();
        for (CableNode node : nodes.values())
        {
            JsonObject nodeObj = new JsonObject();
            node.serializeToJson(nodeObj);
            nodesArray.add(nodeObj);
        }
        root.add("nodes", nodesArray);
        return root;
    }

    public static CableNetwork fromJson(JsonObject json)
    {
        CableNetwork network = new CableNetwork();
        int version = json.has("version") ? json.get("version").getAsInt() : 1;

        if (version >= 2 && json.has("nodes"))
        {
            JsonArray nodesArray = json.getAsJsonArray("nodes");
            for (JsonElement elem : nodesArray)
            {
                JsonObject obj = elem.getAsJsonObject();
                CableNode node = CableNode.deserializeFromJson(obj);
                network.nodes.put(node.getPosition(), node);
            }
            return network;
        }

        if (json.has("links"))
        {
            JsonArray linksArray = json.getAsJsonArray("links");
            for (JsonElement elem : linksArray)
            {
                JsonObject obj = elem.getAsJsonObject();
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
                    network.toggleLink(from, to, face);
                }
            }
        }

        return network;
    }
}
