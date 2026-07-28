package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CableRequestSyncPacket
{
    public CableRequestSyncPacket()
    {
    }

    public CableRequestSyncPacket(FriendlyByteBuf buf)
    {
    }

    public void encode(FriendlyByteBuf buf)
    {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx)
    {
        ctx.get().enqueueWork(() ->
        {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null && sender.serverLevel() != null)
            {
                ServerLevel level = sender.serverLevel();
                CableNetwork network = CableNetworkManager.get(level);
                CableNetworkChannel.sendToPlayer(sender,
                    new CableSyncPacket(SyncHelper.toEntries(network.getAllLinks())));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
