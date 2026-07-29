package com.virtualredstonewire.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.blockentity.CableSignalBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class CableNetworkSavedData extends SavedData
{
    private static final String DATA_NAME = VirtualRedstoneWire.MOD_ID + "_network";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CableNetwork network;

    public CableNetworkSavedData(CableNetwork network)
    {
        this.network = network;
    }

    public CableNetwork getNetwork()
    {
        return network;
    }

    public void setNetwork(CableNetwork network)
    {
        this.network = network;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putString("network", GSON.toJson(network.toJson()));
        return tag;
    }

    public static CableNetworkSavedData load(CompoundTag tag)
    {
        String json = tag.getString("network");
        try
        {
            JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
            CableNetwork network = CableNetwork.fromJson(jsonObj);
            return new CableNetworkSavedData(network);
        }
        catch (Exception e)
        {
            VirtualRedstoneWire.LOGGER.error("Failed to load cable network data", e);
            return new CableNetworkSavedData(new CableNetwork());
        }
    }

    public static CableNetworkSavedData get(ServerLevel level)
    {
        return level.getDataStorage()
            .computeIfAbsent(CableNetworkSavedData::load,
                () -> new CableNetworkSavedData(new CableNetwork()),
                DATA_NAME);
    }

    public void rebuildBlockEntities(ServerLevel level)
    {
        CableNetwork network = getNetwork();
        for (BlockPos pos : network.getAllOutputPositions())
        {
            if (level.getBlockEntity(pos) == null)
            {
                level.setBlockEntity(new CableSignalBlockEntity(pos, level.getBlockState(pos)));
            }
        }
    }
}
