package com.virtualredstonewire.client;

import com.virtualredstonewire.data.CableLink;
import com.virtualredstonewire.network.CableMsgPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

/**
 * v0.3.0 客户端只读缓存：唯一渲染数据源，仅由服务端广播（d）与全量/追回响应（f）驱动更新。
 * 客户端不得直接增删改（保持 setLinks 等旧接口仅用于迁移过渡）。
 */
public class ClientCableCache
{
    private static final List<CableLink> cachedLinks = new ArrayList<>();
    private static long version = 0;

    public static void setLinks(Collection<CableLink> links)
    {
        cachedLinks.clear();
        cachedLinks.addAll(links);
    }

    public static void addLink(BlockPos from, BlockPos to, Direction toFace)
    {
        String id = CableLink.generateId(from, to, toFace);
        for (CableLink link : cachedLinks)
        {
            if (link.getId().equals(id)) return;
        }
        cachedLinks.add(new CableLink(from, to, toFace));
    }

    public static void removeLink(BlockPos from, BlockPos to, Direction toFace)
    {
        String id = CableLink.generateId(from, to, toFace);
        cachedLinks.removeIf(link -> link.getId().equals(id));
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
        String id = CableLink.generateId(from, to, face);
        for (CableLink link : cachedLinks)
        {
            if (link.getId().equals(id)) return true;
        }
        return false;
    }

    public static List<CableLink> getLinks()
    {
        return cachedLinks;
    }

    public static void clear()
    {
        cachedLinks.clear();
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
