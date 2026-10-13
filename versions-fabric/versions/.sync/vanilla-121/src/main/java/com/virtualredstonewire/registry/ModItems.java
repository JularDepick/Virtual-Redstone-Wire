package com.virtualredstonewire.registry;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.item.CableCutterItem;
import com.virtualredstonewire.item.CableMagnifierItem;
import com.virtualredstonewire.item.VirtualCableItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.function.Function;

/*
 * 1.21 适配: ResourceLocation 的公开构造器被移除,
 * 命名空间加路径两参构造改为静态工厂 ResourceLocation.fromNamespaceAndPath。
 */
public class ModItems
{
    public static final Item VIRTUAL_CABLE = register("virtual_cable", VirtualCableItem::new);
    public static final Item CABLE_CUTTER = register("cable_cutter", CableCutterItem::new);
    public static final Item CABLE_MAGNIFIER = register("cable_magnifier", CableMagnifierItem::new);

    private static Item register(String name, Function<Item.Properties, Item> factory)
    {
        return Registry.register(BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(VirtualRedstoneWire.MOD_ID, name),
            factory.apply(new Item.Properties().stacksTo(64)));
    }

    /*
     * 物品加入原版创造模式标签页：Fabric 侧没有 Forge 的事件总线订阅，
     * 改由 Fabric API 的 ItemGroupEvents 在条目构建时追加。
     */
    public static void registerItemGroups()
    {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries ->
        {
            entries.accept(VIRTUAL_CABLE);
            entries.accept(CABLE_CUTTER);
            entries.accept(CABLE_MAGNIFIER);
        });
    }
}
