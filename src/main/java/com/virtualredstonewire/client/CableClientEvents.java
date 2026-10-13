package com.virtualredstonewire.client;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.item.VirtualCableItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端连接/维度处理：进入世界与进入不同维度时，作废旧维度状态并发起当前维度全量请求（sc 为 0）。
 * 不能用 PlayerEvent.PlayerChangedDimensionEvent：Forge 只在服务端 ServerPlayer 上投递该事件
 */
@Mod.EventBusSubscriber(modid = VirtualRedstoneWire.MOD_ID, value = Dist.CLIENT)
public class CableClientEvents
{
    /** 上次完成初始化的维度（报文 dm 字符串格式）；为 null 表示尚未初始化（未进入世界或已登出） */
    private static String lastDimension = null;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() == null) return;

        String dimension = player.level().dimension().location().toString();
        if (dimension.equals(lastDimension)) return;

        /* 换维度：旧维度的缓存/历史/选中态/队列全部作废*/
        if (lastDimension != null)
        {
            ClientCableCache.clear();
            CableUndoRedoManager.clearHistory();
            VirtualCableItem.clearAllSelectedInputs();
            CableClientQueue.onDimensionChanged();
        }
        else
        {
            /* 首次进入世界（登录/重连）：清掉可能的上次会话残留*/
            ClientCableCache.clear();
            CableClientQueue.onDimensionChanged();
        }

        lastDimension = dimension;
        /* 强制通道：绕开 5 秒冷却，否则一次冷却丢弃会让本次进入的维度永远拿不到数据*/
        CableClientQueue.requestFull(true);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        /* 断线/登出：清空缓存、队列与撤销/重做历史、线缆选中态，并重置维度记录（重连后按首次进入世界处理）*/
        lastDimension = null;
        ClientCableCache.clear();
        CableClientQueue.reset();
        VirtualCableItem.clearAllSelectedInputs();
        CableUndoRedoManager.clearHistory();
    }
}
