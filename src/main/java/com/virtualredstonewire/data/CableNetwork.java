package com.virtualredstonewire.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.blockentity.CableSignalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

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

    public boolean toggleLink(BlockPos from, BlockPos to, Direction toFace, Level level)
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
                if (level != null && !level.isClientSide())
                {
                    removeSignalBlockEntity(level, to);
                }
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

            if (level != null && !level.isClientSide())
            {
                createSignalBlockEntity(level, to);
            }

            return true;
        }
    }

    public boolean toggleLink(BlockPos from, BlockPos to, Direction toFace)
    {
        return toggleLink(from, to, toFace, null);
    }

    public int removeLinksFrom(BlockPos from, Level level)
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
                    if (level != null && !level.isClientSide())
                    {
                        removeSignalBlockEntity(level, toPos);
                    }
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

    public int removeLinksFrom(BlockPos from)
    {
        return removeLinksFrom(from, null);
    }

    private void createSignalBlockEntity(Level level, BlockPos pos)
    {
        if (level.getBlockEntity(pos) == null)
        {
            level.setBlockEntity(new CableSignalBlockEntity(pos, level.getBlockState(pos)));
        }
    }

    private void removeSignalBlockEntity(Level level, BlockPos pos)
    {
        if (level.getBlockEntity(pos) instanceof CableSignalBlockEntity)
        {
            level.removeBlockEntity(pos);
        }
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

    public Set<BlockPos> getAllOutputPositions()
    {
        Set<BlockPos> result = new HashSet<>();
        for (CableNode node : nodes.values())
        {
            if (node.getIncomingCount() > 0)
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

        if (version < 1)
        {
            VirtualRedstoneWire.LOGGER.warn("Unknown network data version {}, returning empty network", version);
            return network;
        }

        if (version >= 2 && json.has("nodes"))
        {
            JsonArray nodesArray = json.getAsJsonArray("nodes");
            int skippedCount = 0;
            for (JsonElement elem : nodesArray)
            {
                if (!elem.isJsonObject())
                {
                    skippedCount++;
                    continue;
                }
                JsonObject obj = elem.getAsJsonObject();
                CableNode node = CableNode.deserializeFromJson(obj);
                if (node != null)
                {
                    network.nodes.put(node.getPosition(), node);
                }
                else
                {
                    skippedCount++;
                }
            }
            if (skippedCount > 0)
            {
                VirtualRedstoneWire.LOGGER.warn("Skipped {} invalid nodes during deserialization", skippedCount);
            }
            return network;
        }

        if (version == 1 && json.has("links"))
        {
            VirtualRedstoneWire.LOGGER.info("Loading v1 format network data");
            JsonArray linksArray = json.getAsJsonArray("links");
            for (JsonElement elem : linksArray)
            {
                if (!elem.isJsonObject()) continue;
                JsonObject obj = elem.getAsJsonObject();
                try
                {
                    BlockPos from = new BlockPos(
                        obj.get("fromX").getAsInt(),
                        obj.get("fromY").getAsInt(),
                        obj.get("fromZ").getAsInt());
                    BlockPos to = new BlockPos(
                        obj.get("toX").getAsInt(),
                        obj.get("toY").getAsInt(),
                        obj.get("toZ").getAsInt());
                    Direction face = obj.has("face") ? Direction.byName(obj.get("face").getAsString()) : null;
                    if (face != null)
                    {
                        network.toggleLink(from, to, face);
                    }
                }
                catch (Exception e)
                {
                    VirtualRedstoneWire.LOGGER.warn("Failed to parse v1 link entry: {}", e.getMessage());
                }
            }
        }

        return network;
    }
}
