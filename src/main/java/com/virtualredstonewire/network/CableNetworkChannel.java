package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class CableNetworkChannel
{
    private static final String PROTOCOL_VERSION = "2";
    private static int MESSAGE_ID = 0;

    @SuppressWarnings("removal")
    public static final SimpleChannel CHANNEL =
        NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VirtualRedstoneWire.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register()
    {
        CHANNEL.messageBuilder(CableSyncPacket.class, MESSAGE_ID++)
            .encoder(CableSyncPacket::encode)
            .decoder(CableSyncPacket::new)
            .consumerMainThread(CableSyncPacket::handle)
            .add();

        CHANNEL.messageBuilder(CableActionPacket.class, MESSAGE_ID++)
            .encoder(CableActionPacket::encode)
            .decoder(CableActionPacket::new)
            .consumerMainThread(CableActionPacketHandler::handle)
            .add();

        CHANNEL.messageBuilder(CableRequestSyncPacket.class, MESSAGE_ID++)
            .encoder(CableRequestSyncPacket::encode)
            .decoder(CableRequestSyncPacket::new)
            .consumerMainThread(CableRequestSyncPacket::handle)
            .add();

        CHANNEL.messageBuilder(CableInfoRequestPacket.class, MESSAGE_ID++)
            .encoder(CableInfoRequestPacket::encode)
            .decoder(CableInfoRequestPacket::new)
            .consumerMainThread(CableInfoRequestPacket::handle)
            .add();

        CHANNEL.messageBuilder(CableInfoResponsePacket.class, MESSAGE_ID++)
            .encoder(CableInfoResponsePacket::encode)
            .decoder(CableInfoResponsePacket::new)
            .consumerMainThread(CableInfoResponsePacket::handle)
            .add();
    }

    public static void sendToAll(ServerLevel level, Object packet)
    {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    public static void sendToServer(Object packet)
    {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
