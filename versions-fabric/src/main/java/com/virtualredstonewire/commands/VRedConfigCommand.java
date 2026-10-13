package com.virtualredstonewire.commands;

import com.virtualredstonewire.client.gui.ClientConfigScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;

/**
 * 客户端命令 /vredconfig：打开本模组的配置界面。
 */
public class VRedConfigCommand
{
    private VRedConfigCommand() {}

    /** 注册客户端命令（由客户端入口调用） */
    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            /* 客户端命令必须用 ClientCommandManager 构造, 它产出的是客户端命令源类型*/
            dispatcher.register(ClientCommandManager.literal("vredconfig")
                .executes(context ->
                {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> client.setScreen(new ClientConfigScreen(null)));
                    return 1;
                })));
    }
}
