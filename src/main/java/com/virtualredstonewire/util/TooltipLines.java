package com.virtualredstonewire.util;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 物品 tooltip 多行工具。
 * 1.20.1 的 tooltip 渲染不按转义换行符换行，
 * 需将翻译文本按行拆分后逐行添加 Component。
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
