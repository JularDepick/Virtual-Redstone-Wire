package com.virtualredstonewire.client;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.network.CableOpPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * v0.3.0 客户端连接/维度事件：
 * 连接服务端与进入不同维度时，发起当前维度全量请求（sc 为 0）。
 */
@Mod.EventBusSubscriber(modid = VirtualRedstoneWire.MOD_ID, value = Dist.CLIENT)
public class CableClientEvents
{
    private static boolean initialized = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        if (initialized) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() == null) return;
        CableClientQueue.enqueue(
            CableOpPacket.pull(0, player.level().dimension(), player.getName().getString()));
        initialized = true;
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof LocalPlayer)
        {
            ClientCableCache.clear();
            initialized = false;
        }
    }
}
