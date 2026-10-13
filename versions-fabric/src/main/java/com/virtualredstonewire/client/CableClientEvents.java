package com.virtualredstonewire.client;

import com.virtualredstonewire.item.VirtualCableItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 客户端连接/维度处理：进入世界与进入不同维度时，作废旧维度状态并发起当前维度全量请求（sc 为 0）。
 * 维度隔离失效教训：原先的换维度处理监听维度切换事件，
 */
public class CableClientEvents
{
    /** 上次完成初始化的维度（报文 dm 字符串格式）；为 null 表示尚未初始化（未进入世界或已登出） */
    private static String lastDimension = null;

    /** 注册逐 tick 维度比对与断线处理 */
    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onLoggingOut());
    }

    private static void onClientTick()
    {
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

    private static void onLoggingOut()
    {
        /* 断线/登出：清空缓存、队列与撤销/重做历史、线缆选中态，并重置维度记录（重连后按首次进入世界处理）*/
        lastDimension = null;
        ClientCableCache.clear();
        CableClientQueue.reset();
        VirtualCableItem.clearAllSelectedInputs();
        CableUndoRedoManager.clearHistory();
    }
}
