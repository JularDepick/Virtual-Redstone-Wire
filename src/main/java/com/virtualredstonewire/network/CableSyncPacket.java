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
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class CableSyncPacket
{
    private final List<CableLink> links;

    public CableSyncPacket(Collection<CableLink> links)
    {
        this.links = new ArrayList<>(links);
    }

    public CableSyncPacket(FriendlyByteBuf buf)
    {
        int size = buf.readVarInt();
        this.links = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
        {
            String id = buf.readUtf();
            BlockPos from = buf.readBlockPos();
            BlockPos to = buf.readBlockPos();
            Direction face = buf.readEnum(Direction.class);
            links.add(new CableLink(id, from, to, face));
        }
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeVarInt(links.size());
        for (CableLink link : links)
        {
            buf.writeUtf(link.getId());
            buf.writeBlockPos(link.getFrom());
            buf.writeBlockPos(link.getTo());
            buf.writeEnum(link.getToFace());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientCableCache.setLinks(links));
        });
        ctx.get().setPacketHandled(true);
    }
}
