package com.virtualredstonewire;

import com.virtualredstonewire.client.CableActionBarHud;
import com.virtualredstonewire.client.CableClientEvents;
import com.virtualredstonewire.client.CableInteractGate;
import com.virtualredstonewire.client.CableUndoRedoManager;
import com.virtualredstonewire.client.render.CableRenderer;
import com.virtualredstonewire.commands.VRedConfigCommand;
import com.virtualredstonewire.config.ClientConfig;
import com.virtualredstonewire.network.CableNetworkChannel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/*
 * 客户端入口。
 * 因此所有客户端侧注册都必须在入口里显式接线。漏接不会报编译错误，
 * 只会表现为对应功能静默失效，所以这里逐项列出并注明各自负责什么。
 */
public class ClientSetup implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        /* Fabric 侧没有加载器代管的配置系统，客户端配置由模组自行读取*/
        ClientConfig.INSTANCE.load(FabricLoader.getInstance().getConfigDir());

        /* 撤销/重做与历史窗口页：注册三个按键并挂上逐 tick 处理*/
        CableUndoRedoManager.register();

        /* 三个交互物品的右键按下沿闸门：逐 tick 兜底复位锁存位*/
        CableInteractGate.register();

        /* 快捷栏上方飘浮提示的 HUD 渲染回调*/
        CableActionBarHud.register();

        /* 逐 tick 维度比对与断线复位*/
        CableClientEvents.register();

        /* 客户端接收器（服务端 -> 客户端）*/
        CableNetworkChannel.registerClient();

        /* 配置界面入口：Fabric 侧没有 Mods 菜单配置入口，改用客户端命令*/
        VRedConfigCommand.register();

        /* 线缆高亮渲染与渲染相关的客户端 tick*/
        CableRenderer.register();

    }
}
