package com.virtualredstonewire;

import com.mojang.logging.LogUtils;
import com.virtualredstonewire.config.ModConfig;
import com.virtualredstonewire.network.CableNetworkChannel;
import com.virtualredstonewire.registry.ModBlocks;
import com.virtualredstonewire.registry.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(VirtualRedstoneWire.MOD_ID)
public class VirtualRedstoneWire
{
    public static final String MOD_ID = "virtual-redstone-wire";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VirtualRedstoneWire()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);

        ModLoadingContext.get().registerConfig(Type.COMMON, ModConfig.SPEC, MOD_ID + "-common.toml");

        CableNetworkChannel.register();

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> {
                modEventBus.addListener(com.virtualredstonewire.client.ClientSetup::onClientSetup);
            });

        LOGGER.info("Virtual Redstone Wire initialized");
    }
}
