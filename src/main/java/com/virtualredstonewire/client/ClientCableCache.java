package com.virtualredstonewire.client;

import com.virtualredstonewire.data.CableLink;
import com.virtualredstonewire.network.CableMsgPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

/**
 * v0.3.0 客户端只读缓存：唯一渲染数据源，仅由服务端广播（d）与全量/追回响应（f）驱动更新。
 * v0.5.1 索引化：保序列表 + 按链路唯一标识索引的映射表，存在性判断/去重/精确移除 O(1)。
 * 客户端不得直接增删改缓存。
 */
public class ClientCableCache
{
    private static final List<CableLink> cachedLinks = new ArrayList<>();
    private static final Map<String, CableLink> indexById = new HashMap<>();
    private static long version = 0;

    public static void addLink(BlockPos from, BlockPos to, Direction toFace)
    {
        String id = CableLink.generateId(from, to, toFace);
        if (indexById.containsKey(id)) return;
        CableLink link = new CableLink(from, to, toFace);
        cachedLinks.add(link);
        indexById.put(id, link);
    }

    public static void removeLink(BlockPos from, BlockPos to, Direction toFace)
    {
        String id = CableLink.generateId(from, to, toFace);
        CableLink link = indexById.remove(id);
        if (link == null) return;
        cachedLinks.remove(link);
    }

    public static boolean hasLinkFrom(BlockPos from)
    {
        for (CableLink link : cachedLinks)
        {
            if (link.getFrom().equals(from)) return true;
        }
        return false;
    }

    public static boolean hasLinkAt(BlockPos pos)
    {
        for (CableLink link : cachedLinks)
        {
            if (link.getFrom().equals(pos) || link.getTo().equals(pos)) return true;
        }
        return false;
    }

    public static boolean hasLink(BlockPos from, BlockPos to, Direction face)
    {
        return indexById.containsKey(CableLink.generateId(from, to, face));
    }

    public static List<CableLink> getLinks()
    {
        return cachedLinks;
    }

    public static void clear()
    {
        cachedLinks.clear();
        indexById.clear();
        version = 0;
    }

    // ── v0.3.0 版本化更新 ──────────────────────────

    public static long getVersion()
    {
        return version;
    }

    /** 应用增量（tp 为 d，或追回响应）。版本号必须大于自身才接受。 */
    public static void applyDelta(List<CableMsgPacket.Change> changes, long newVersion)
    {
        if (newVersion <= version) return;
        for (CableMsgPacket.Change c : changes)
        {
            BlockPos fr = new BlockPos(c.fx(), c.fy(), c.fz());
            BlockPos to = new BlockPos(c.tx(), c.ty(), c.tz());
            Direction face = Direction.byName(c.face());
            if (com.virtualredstonewire.network.CableProtocol.OP_ADD.equals(c.op()))
            {
                addLink(fr, to, face);
            }
            else
            {
                removeLink(fr, to, face);
            }
        }
        version = newVersion;
    }

    /** 覆盖缓存（sc 为 0 的全量响应） */
    public static void applyFull(List<CableMsgPacket.Change> changes, long newVersion)
    {
        cachedLinks.clear();
        indexById.clear();
        for (CableMsgPacket.Change c : changes)
        {
            if (com.virtualredstonewire.network.CableProtocol.OP_ADD.equals(c.op()))
            {
                addLink(new BlockPos(c.fx(), c.fy(), c.fz()),
                    new BlockPos(c.tx(), c.ty(), c.tz()), Direction.byName(c.face()));
            }
        }
        version = newVersion;
    }
}
