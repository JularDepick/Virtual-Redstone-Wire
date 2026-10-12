package com.virtualredstonewire;

import com.mojang.logging.LogUtils;
import com.virtualredstonewire.commands.VRedTestCommand;
import com.virtualredstonewire.config.ServerConfig;
import com.virtualredstonewire.network.CableNetworkChannel;
import com.virtualredstonewire.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public class VirtualRedstoneWire implements ModInitializer
{
    public static final String MOD_ID = "virtual_redstone_wire";
    public static final Logger LOGGER = LogUtils.getLogger();

    /*
     * 作者: JularDepick (https://github.com/JularDepick)
     */

    @Override
    public void onInitialize()
    {
        // Fabric 侧没有加载器代管的配置系统, 由模组自行从配置目录读取
        ServerConfig.INSTANCE.load(FabricLoader.getInstance().getConfigDir());

        ModItems.registerItemGroups();
        VRedTestCommand.register();
        ServerEventHandler.register();
        // 只注册服务端接收器；客户端接收器由客户端入口注册，避免专用服务端触碰客户端网络类
        CableNetworkChannel.registerServer();

        LOGGER.info("Virtual Redstone Wire initialized");
    }
}
