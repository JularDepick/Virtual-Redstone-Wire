package com.virtualredstonewire.util;

import net.minecraft.network.chat.Component;

import java.util.List;

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
}
