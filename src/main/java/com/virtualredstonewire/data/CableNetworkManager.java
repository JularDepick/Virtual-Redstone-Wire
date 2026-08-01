package com.virtualredstonewire.data;

import com.virtualredstonewire.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CableNetworkManager
{
    private static final Map<ResourceKey<Level>, CableNetwork> NETWORKS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Boolean> DIRTY_FLAGS = new HashMap<>();

    // v0.3.0：每维度操作计数器与近期版本变更表（内存驻留，不落盘）
    private static final Map<ResourceKey<Level>, Long> VERSIONS = new HashMap<>();
    private static final Map<ResourceKey<Level>, List<ChangeRecord>> CHANGE_LOGS = new HashMap<>();

    /** 变更表记录（完整字段名，映射到通信响应时转换为缩写键名） */
    public record ChangeRecord(long version, String player, String op,
                               BlockPos from, BlockPos to, Direction face) {}

    public static CableNetwork get(Level level)
    {
        return NETWORKS.computeIfAbsent(level.dimension(), k -> new CableNetwork());
    }

    public static void load(ResourceKey<Level> dimension, CableNetwork network)
    {
        NETWORKS.put(dimension, network);
        // 计数器与变更表：进入该维度后重新计数（与存档数据维度隔离一致）
        resetVersion(dimension);
    }

    public static void markDirty(Level level)
    {
        DIRTY_FLAGS.put(level.dimension(), true);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel)
        {
            CableNetworkSavedData.get(serverLevel).setDirty();
        }
    }

    public static boolean isDirty(ResourceKey<Level> dimension)
    {
        return DIRTY_FLAGS.getOrDefault(dimension, false);
    }

    public static void clearDirty(ResourceKey<Level> dimension)
    {
        DIRTY_FLAGS.put(dimension, false);
    }

    public static void onDimensionUnload(ResourceKey<Level> dimension)
    {
        NETWORKS.remove(dimension);
        DIRTY_FLAGS.remove(dimension);
        resetVersion(dimension);
    }

    // ── v0.3.0 版本号与变更表 ──────────────────────

    public static long currentVersion(ResourceKey<Level> dimension)
    {
        return VERSIONS.getOrDefault(dimension, 0L);
    }

    /** 执行一批操作后调用：计数器加一，返回本批版本号 */
    public static long nextVersion(ResourceKey<Level> dimension)
    {
        long v = currentVersion(dimension) + 1;
        VERSIONS.put(dimension, v);
        return v;
    }

    public static void recordChange(ResourceKey<Level> dimension, ChangeRecord record)
    {
        List<ChangeRecord> list = CHANGE_LOGS.computeIfAbsent(dimension, k -> new ArrayList<>());
        list.add(record);
        int cap = ServerConfig.networkChangeLogSize.get();
        while (list.size() > cap)
        {
            list.remove(0);
        }
    }

    /** 返回 (since, current] 区间内的变更记录；落后超出容量返回 null 表示过期 */
    public static List<ChangeRecord> getChangesSince(ResourceKey<Level> dimension, long since)
    {
        List<ChangeRecord> list = CHANGE_LOGS.getOrDefault(dimension, new ArrayList<>());
        if (currentVersion(dimension) - since > ServerConfig.networkChangeLogSize.get())
        {
            return null;
        }
        List<ChangeRecord> result = new ArrayList<>();
        for (ChangeRecord rec : list)
        {
            if (rec.version() > since)
            {
                result.add(rec);
            }
        }
        return result;
    }

    public static void resetVersion(ResourceKey<Level> dimension)
    {
        VERSIONS.remove(dimension);
        CHANGE_LOGS.remove(dimension);
    }
}
