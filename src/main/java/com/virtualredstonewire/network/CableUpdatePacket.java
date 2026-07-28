package com.virtualredstonewire.network;

import com.virtualredstonewire.client.ClientCableCache;
import com.virtualredstonewire.data.CableLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CableUpdatePacket
{
    private final boolean added;
    private final BlockPos from;
    private final BlockPos to;
    private final Direction toFace;

    public CableUpdatePacket(boolean added, BlockPos from, BlockPos to, Direction toFace)
    {
        this.added = added;
        this.from = from.immutable();
        this.to = to.immutable();
        this.toFace = toFace;
    }

    public CableUpdatePacket(FriendlyByteBuf buf)
    {
        this.added = buf.readBoolean();
        this.from = buf.readBlockPos();
        this.to = buf.readBlockPos();
        this.toFace = buf.readEnum(Direction.class);
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeBoolean(added);
        buf.writeBlockPos(from);
        buf.writeBlockPos(to);
        buf.writeEnum(toFace);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    if (added)
                    {
                        ClientCableCache.addLink(from, to, toFace);
                    }
                    else
                    {
                        ClientCableCache.removeLink(from, to, toFace);
                    }
                });
        });
        ctx.get().setPacketHandled(true);
    }
}
