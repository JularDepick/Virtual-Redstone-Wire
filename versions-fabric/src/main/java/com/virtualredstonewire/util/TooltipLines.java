package com.virtualredstonewire.util;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 物品 tooltip 多行工具。
 */
public final class TooltipLines
{
    private TooltipLines() {}

    /** 按换行符拆分翻译文本并逐行添加 */
    public static void add(List<Component> tooltip, String translationKey)
    {
        String text = Component.translatable(translationKey).getString();
        for (String line : text.split("\n"))
        {
            tooltip.add(Component.literal(line));
        }
    }
}
