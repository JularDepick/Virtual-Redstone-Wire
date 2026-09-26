package com.virtualredstonewire.client;

import com.virtualredstonewire.data.CableLink;
import com.virtualredstonewire.network.CableMsgPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * v0.3.0 客户端只读缓存：唯一渲染数据源，仅由服务端广播（d）与全量/追回响应（f）驱动更新。
 * v0.5.1 索引化：保序列表 + 按链路唯一标识索引的映射表，存在性判断/去重/精确移除 O(1)。
 * v1.0.0：新增所属维度标记，写入入口按报文维度自校验（维度变化时先整体作废），
 * 渲染与交互侧据此判定缓存是否属于当前维度，防止维度隔离失效。
 * 客户端不得直接增删改缓存。
 */
public class ClientCableCache
{
    private static final List<CableLink> cachedLinks = new ArrayList<>();
    private static final Map<String, CableLink> indexById = new HashMap<>();
    private static long version = 0;
    /** 缓存内容所属维度（报文 dm 字符串）；为 null 表示缓存无有效数据（未进入世界或已作废） */
    private static String dimension = null;

    /** 内部更新入口：仅供增量/全量应用调用，外部只读 */
    private static void addLink(BlockPos from, BlockPos to, Direction toFace)
    {
        String id = CableLink.generateId(from, to, toFace);
        if (indexById.containsKey(id)) return;
        CableLink link = new CableLink(from, to, toFace);
        cachedLinks.add(link);
        indexById.put(id, link);
    }

    /** 内部更新入口：仅供增量应用调用，外部只读 */
    private static void removeLink(BlockPos from, BlockPos to, Direction toFace)
    {
        String id = CableLink.generateId(from, to, toFace);
        CableLink link = indexById.remove(id);
        if (link == null) return;
        cachedLinks.remove(link);
    }

    public static boolean hasLink(BlockPos from, BlockPos to, Direction face)
    {
        return indexById.containsKey(CableLink.generateId(from, to, face));
    }

    public static List<CableLink> getLinks()
    {
        // v1.0.0：对外只读视图，避免调用方误改内部结构与遍历中出现并发修改
        return Collections.unmodifiableList(cachedLinks);
    }

    /** 缓存内容所属维度；无有效数据时为 null */
    public static String getDimension()
    {
        return dimension;
    }

    /** 缓存内容是否属于给定维度；缓存为空或维度不符时返回 false */
    public static boolean isCurrentFor(Level level)
    {
        return level != null && dimension != null
            && dimension.equals(level.dimension().location().toString());
    }

    public static void clear()
    {
        cachedLinks.clear();
        indexById.clear();
        version = 0;
        dimension = null;
    }

    // ── v0.3.0 版本化更新 ──────────────────────────

    public static long getVersion()
    {
        return version;
    }

    /**
     * 应用增量（tp 为 d，或追回响应）。版本号必须大于自身才接受。
     * v1.0.0：写入前按报文维度自校验，维度变化时先整体作废，避免两个维度的内容混合。
     */
    public static void applyDelta(String dim, List<CableMsgPacket.Change> changes, long newVersion)
    {
        syncDimension(dim);
        if (newVersion <= version) return;
        for (CableMsgPacket.Change c : changes)
        {
            BlockPos fr = new BlockPos(c.fx(), c.fy(), c.fz());
            BlockPos to = new BlockPos(c.tx(), c.ty(), c.tz());
            Direction face = Direction.byName(c.face());
            if (face == null) continue; // v1.0.0：非法输出面丢弃该条，避免写入 null 面
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

    /** 覆盖缓存（sc 为 0 的全量响应）；v1.0.0：同维度内旧版本快照不覆盖较新缓存 */
    public static void applyFull(String dim, List<CableMsgPacket.Change> changes, long newVersion)
    {
        boolean sameDimension = dim != null && dim.equals(dimension);
        syncDimension(dim);
        if (sameDimension && newVersion < version) return;
        cachedLinks.clear();
        indexById.clear();
        for (CableMsgPacket.Change c : changes)
        {
            if (com.virtualredstonewire.network.CableProtocol.OP_ADD.equals(c.op()))
            {
                Direction face = Direction.byName(c.face());
                if (face == null) continue; // v1.0.0：非法输出面丢弃该条
                addLink(new BlockPos(c.fx(), c.fy(), c.fz()),
                    new BlockPos(c.tx(), c.ty(), c.tz()), face);
            }
        }
        version = newVersion;
    }

    /**
     * 写入前维度自校验：缓存为空时直接认领该维度；维度与缓存不一致时先整体作废，
     * 保证缓存内不会同时存在两个维度的链路。
     */
    private static void syncDimension(String dim)
    {
        if (dim == null) return;
        if (!dim.equals(dimension))
        {
            cachedLinks.clear();
            indexById.clear();
            version = 0;
        }
        dimension = dim;
    }
}
