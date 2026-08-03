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
        ModContainer modContainer = context.getContainer();

        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);

        modContainer.addConfig(new ModConfig(ModConfig.Type.SERVER, ServerConfig.SPEC,
            modContainer, MOD_ID + "-server.toml"));

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> {
                modContainer.addConfig(new ModConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC,
                    modContainer, MOD_ID + "-client.toml"));
                ClientSetup.registerConfigScreen(modContainer);
                modEventBus.addListener(ClientSetup::onClientSetup);
            });

        CableNetworkChannel.register();

        LOGGER.info("Virtual Redstone Wire initialized");
    }
}
