package com.virtualredstonewire.client;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.item.VirtualCableItem;
import com.virtualredstonewire.network.CableOpPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * v0.3.0 客户端连接/维度事件：
 * 连接服务端与进入不同维度时，发起当前维度全量请求（sc 为 0）；
 * 断线重连后重置初始化标记，重连成功后的下一 tick 自动重新全量拉取。
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
        CableClientQueue.requestFull();
        initialized = true;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        // 断线/登出：重置初始化标记（重连后自动全量），并清空线缆选中态（防跨会话残留）
        initialized = false;
        VirtualCableItem.clearAllSelectedInputs();
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
