package com.virtualredstonewire.registry;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.item.CableCutterItem;
import com.virtualredstonewire.item.CableMagnifierItem;
import com.virtualredstonewire.item.VirtualCableItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems
{
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, VirtualRedstoneWire.MOD_ID);

    public static final RegistryObject<Item> VIRTUAL_CABLE = ITEMS.register("virtual_cable",
        () -> new VirtualCableItem(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> CABLE_CUTTER = ITEMS.register("cable_cutter",
        () -> new CableCutterItem(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> CABLE_MAGNIFIER = ITEMS.register("cable_magnifier",
        () -> new CableMagnifierItem(new Item.Properties().stacksTo(64)));

    @Mod.EventBusSubscriber(modid = VirtualRedstoneWire.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class TabRegistration
    {
        @SubscribeEvent
        public static void buildContents(BuildCreativeModeTabContentsEvent event)
        {
            if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.REDSTONE_BLOCKS)
            {
                event.accept(VIRTUAL_CABLE);
                event.accept(CABLE_CUTTER);
                event.accept(CABLE_MAGNIFIER);
            }
        }
    }
}