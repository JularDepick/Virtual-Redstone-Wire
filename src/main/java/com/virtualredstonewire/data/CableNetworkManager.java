package com.virtualredstonewire.data;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class CableNetworkManager
{
    private static final Map<ResourceKey<Level>, CableNetwork> NETWORKS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Boolean> DIRTY_FLAGS = new HashMap<>();

    public static CableNetwork get(Level level)
    {
        return NETWORKS.computeIfAbsent(level.dimension(), k -> new CableNetwork());
    }

    public static void load(ResourceKey<Level> dimension, CableNetwork network)
    {
        NETWORKS.put(dimension, network);
    }

    public static void markDirty(Level level)
    {
        DIRTY_FLAGS.put(level.dimension(), true);
        if (level instanceof ServerLevel serverLevel)
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
    }
}
