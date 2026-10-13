package com.virtualredstonewire.util;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 物品 tooltip 多行工具。
 */
public final class TooltipLines
{
    private TooltipLines() {}

    public static void add(List<Component> tooltip, String translationKey)
    {
        String text = Component.translatable(translationKey).getString();
        for (String line : text.split("\n"))
        {
            tooltip.add(Component.literal(line));
        }
    }

    /** 物品提示改为 Consumer 形式, 此处委托既有实现, 避免提示文案逻辑分叉 */
    public static void add(Consumer<Component> sink, String key)
    {
        List<Component> lines = new java.util.ArrayList<>();
        add(lines, key);
        lines.forEach(sink);
    }
}
