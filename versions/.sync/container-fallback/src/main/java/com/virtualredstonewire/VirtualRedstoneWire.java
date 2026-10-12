package com.virtualredstonewire;

import com.mojang.logging.LogUtils;
import com.virtualredstonewire.config.ClientConfig;
import com.virtualredstonewire.config.ServerConfig;
import com.virtualredstonewire.network.CableNetworkChannel;
import com.virtualredstonewire.registry.ModBlocks;
import com.virtualredstonewire.registry.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(VirtualRedstoneWire.MOD_ID)
public class VirtualRedstoneWire
{
    public static final String MOD_ID = "virtual_redstone_wire";
    public static final Logger LOGGER = LogUtils.getLogger();

    /*
     * 作者: JularDepick (https://github.com/JularDepick)
     */

    public VirtualRedstoneWire(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        // 48.x 起 FMLJavaModLoadingContext 不再提供 getContainer(), 改用 ModLoadingContext 的当前容器。
        // 这里用 getActiveContainer() 而非 getContainer(): 后者在 48.1 尚不存在,
        // 而前者在 48.1 / 49.x / 50.x 均可用(49.2 起标记 deprecated, 仅告警不影响),
        // 因此它是覆盖 48.x-50.x 的合并产物在两代都成立的写法。
        ModContainer modContainer = ModLoadingContext.get().getActiveContainer();

        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);

        modContainer.addConfig(new ModConfig(ModConfig.Type.SERVER, ServerConfig.SPEC,
            modContainer, MOD_ID + "-server.toml"));

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> {
                modContainer.addConfig(new ModConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC,
                    modContainer, MOD_ID + "-client.toml"));
                ClientSetup.registerConfigScreen(modContainer);
                // v1.0.0：ClientSetup 已由类级 @Mod.EventBusSubscriber(MOD bus) 自动注册，
                // 此处不再重复 addListener（原实现会让 onClientSetup 被调用两次）
            });

        CableNetworkChannel.register();

        LOGGER.info("Virtual Redstone Wire initialized");
    }
}
