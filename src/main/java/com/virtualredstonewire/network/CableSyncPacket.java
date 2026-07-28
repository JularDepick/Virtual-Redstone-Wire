package com.virtualredstonewire.network;

import com.virtualredstonewire.client.ClientCableCache;
import com.virtualredstonewire.data.CableLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CableSyncPacket
{
    private final List<BlockPos> fromPositions;
    private final List<BlockPos> toPositions;
    private final List<Direction> toFaces;

    public CableSyncPacket(List<CableSyncEntry> entries)
    {
        this.fromPositions = new ArrayList<>();
        this.toPositions = new ArrayList<>();
        this.toFaces = new ArrayList<>();
        for (CableSyncEntry e : entries)
        {
            fromPositions.add(e.from());
            toPositions.add(e.to());
            toFaces.add(e.face());
        }
    }

    public CableSyncPacket(FriendlyByteBuf buf)
    {
        int size = buf.readVarInt();
        this.fromPositions = new ArrayList<>(size);
        this.toPositions = new ArrayList<>(size);
        this.toFaces = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
        {
            fromPositions.add(buf.readBlockPos());
            toPositions.add(buf.readBlockPos());
            toFaces.add(buf.readEnum(Direction.class));
        }
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeVarInt(fromPositions.size());
        for (int i = 0; i < fromPositions.size(); i++)
        {
            buf.writeBlockPos(fromPositions.get(i));
            buf.writeBlockPos(toPositions.get(i));
            buf.writeEnum(toFaces.get(i));
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
        {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            {
                List<CableLink> links = new ArrayList<>();
                for (int i = 0; i < fromPositions.size(); i++)
                {
                    links.add(new CableLink(
                        fromPositions.get(i), toPositions.get(i), toFaces.get(i)));
                }
                ClientCableCache.setLinks(links);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    public record CableSyncEntry(BlockPos from, BlockPos to, Direction face) {}
}
