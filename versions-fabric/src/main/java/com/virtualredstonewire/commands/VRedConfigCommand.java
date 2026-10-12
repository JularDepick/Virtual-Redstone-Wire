package com.virtualredstonewire.commands;

import com.virtualredstonewire.client.gui.ClientConfigScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;

/**
 * 客户端命令 /vredconfig：打开本模组的配置界面。
 *
 * Forge 侧把配置界面注册进 Mods 菜单，Fabric 侧没有对应机制，若不补入口，
 * 使用者就只能手改配置文件。这里用客户端命令作为入口：不引入第三方依赖，
 * 且命令可被 Tab 补全，比新增一个快捷键更易发现。
 */
public class VRedConfigCommand
{
    private VRedConfigCommand() {}

    /** 注册客户端命令（由客户端入口调用） */
    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            // 客户端命令必须用 ClientCommandManager 构造, 它产出的是客户端命令源类型
            dispatcher.register(ClientCommandManager.literal("vredconfig")
                .executes(context ->
                {
                    Minecraft client = Minecraft.getInstance();
                    client.execute(() -> client.setScreen(new ClientConfigScreen(null)));
                    return 1;
                })));
    }
}
