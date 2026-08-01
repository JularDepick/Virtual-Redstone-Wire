package com.virtualredstonewire.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class CableNetwork
{
    /** 默认通道名（与 DBW 一致：非多通道源一律走 "world" 通道）。 */
    public static final String WORLD_CHANNEL = "world";

    private final Map<BlockPos, CableNode> nodes = new HashMap<>();

    /**
     * 存储式信号：输出端 (out, side) -> 通道 -> 信号强度。
     * 与 DBW 的 WireNetworkNode.inputs 对应：信号由事件主动写入存储，查询时纯读，
     * 不落盘（仅在内存中，存档加载后由事件/refreshSource 重建）。
     */
    private final Map<BlockPos, Map<Direction, Map<String, Integer>>> signals = new HashMap<>();

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

            // DBW 语义：删链后将该输出端存储信号清零，并立即触发邻居更新让灯熄灭
            setChannelSignal(level, to, toFace, WORLD_CHANNEL, 0);

            if (outputNode.getIncomingCount() == 0 && outputNode.getOutgoingCount() == 0)
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
                if (targetNode.getIncomingCount() == 0 && targetNode.getOutgoingCount() == 0)
                {
                    removeNode(toPos);
                }
            }
            // DBW 语义：删链后清零该输出端存储信号并触发邻居更新
            setChannelSignal(level, toPos, toFace, WORLD_CHANNEL, 0);
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

    /**
     * DBW 语义存储式信号查询：返回输出端方块 out 在 side 面上存储的网络信号
     * （所有通道的最大值）。查询方为 out.relative(side)（该方块查询"out 朝我
     * 发射的信号"时命中节点 (out, side)）。
     */
    public int getSignalAt(BlockPos out, Direction side)
    {
        Map<Direction, Map<String, Integer>> sideSignals = signals.get(out.immutable());
        if (sideSignals == null) return 0;
        Map<String, Integer> channelSignals = sideSignals.get(side);
        if (channelSignals == null || channelSignals.isEmpty()) return 0;
        int maxSignal = 0;
        for (int signal : channelSignals.values())
        {
            if (signal > maxSignal) maxSignal = signal;
        }
        return maxSignal;
    }

    /**
     * 写入输出端 (out, side) 指定通道的存储信号；值有变化时返回 true，
     * 并触发 out.relative(side) 的邻居更新（DBW WireNetworkSink.setInput 语义：
     * 让红石灯等查询方立即重查信号）。
     */
    public boolean setChannelSignal(Level level, BlockPos out, Direction side,
                                    String channel, int signal)
    {
        BlockPos key = out.immutable();

        // 先只读检查旧值，避免 0→0 时创建空容器（轻微内存泄漏）
        Map<Direction, Map<String, Integer>> sideSignals = signals.get(key);
        Integer old = null;
        if (sideSignals != null)
        {
            Map<String, Integer> channelSignals = sideSignals.get(side);
            if (channelSignals != null) old = channelSignals.get(channel);
        }
        int oldSignal = old == null ? 0 : old;
        if (oldSignal == signal) return false;

        sideSignals = signals.computeIfAbsent(key, k -> new HashMap<>());
        Map<String, Integer> channelSignals =
            sideSignals.computeIfAbsent(side, k -> new HashMap<>());

        if (signal == 0)
        {
            channelSignals.remove(channel);
            if (channelSignals.isEmpty())
            {
                sideSignals.remove(side);
                if (sideSignals.isEmpty())
                {
                    signals.remove(key);
                }
            }
        }
        else
        {
            channelSignals.put(channel, signal);
        }

        if (level != null)
        {
            level.updateNeighborsAt(out.relative(side),
                level.getBlockState(out.relative(side)).getBlock());
        }
        return true;
    }

    /**
     * DBW ShipWireNetworkManager.setSource 语义：
     * 源方块 srcPos 的某个通道信号变化为 signal 时，遍历该源所有输出端
     * （sink），逐个写入存储信号并触发邻居更新。
     */
    public void setSource(Level level, BlockPos srcPos, String channel, int signal)
    {
        CableNode node = getNode(srcPos);
        if (node == null || node.getOutgoingCount() == 0) return;
        for (Map.Entry<BlockPos, Direction> edge : node.getOutgoing())
        {
            setChannelSignal(level, edge.getKey(), edge.getValue(), channel, signal);
        }
    }

    /**
     * 主动采集源方块当前真实信号并写入存储（世界加载后重建 / 建链后立即生效）：
     * 与 DBW ServerEvents.onBlockUpdate 的两分支一致——
     * 信号源方块取 6 方向 getSignal 最大值，非信号源取 getBestNeighborSignal。
     */
    public void refreshSource(Level level, BlockPos srcPos)
    {
        CableNode node = getNode(srcPos);
        if (node == null || node.getOutgoingCount() == 0) return;

        BlockState state = level.getBlockState(srcPos);
        int signal;
        if (state.isSignalSource())
        {
            signal = 0;
            for (Direction d : Direction.values())
            {
                signal = Math.max(signal, state.getSignal(level, srcPos, d));
            }
        }
        else
        {
            signal = level.getBestNeighborSignal(srcPos);
        }
        setSource(level, srcPos, WORLD_CHANNEL, signal);
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
