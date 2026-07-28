package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class CableNetworkChannel
{
    private static final String PROTOCOL_VERSION = "1";
    private static int ID = 0;

    public static final SimpleChannel CHANNEL =
        NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VirtualRedstoneWire.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register()
    {
        CHANNEL.messageBuilder(CableSyncPacket.class, ID++)
            .encoder(CableSyncPacket::encode)
            .decoder(CableSyncPacket::new)
            .consumerMainThread(CableSyncPacket::handle)
            .add();

        CHANNEL.messageBuilder(CableUpdatePacket.class, ID++)
            .encoder(CableUpdatePacket::encode)
            .decoder(CableUpdatePacket::new)
            .consumerMainThread(CableUpdatePacket::handle)
            .add();

        CHANNEL.messageBuilder(CableQueryPacket.class, ID++)
            .encoder(CableQueryPacket::encode)
            .decoder(CableQueryPacket::new)
            .consumerMainThread(CableQueryPacket::handle)
            .add();
    }

    public static void sendToAll(ServerLevel level, Object packet)
    {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    public static void sendToAllTracking(ServerLevel level, BlockPos pos, Object packet)
    {
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK
            .with(() -> level.getChunkAt(pos)), packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
