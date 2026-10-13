package com.virtualredstonewire.util;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 物品 tooltip 多行工具。
 * 1.20.1 的 tooltip 渲染不按转义换行符换行，
 * 需将翻译文本按行拆分后逐行添加 Component。
 *
 * 1.21.5 适配: Item#appendHoverText 的收集容器由 List<Component> 改为 Consumer<Component>,
 * 因此这里增补 Consumer 形式的重载: 内部仍走既有的 List 实现, 再逐行喂给 sink,
 * 使提示文案的拆分逻辑只有一处, 两个入口不会分叉。
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

    /** 1.21.5 起的 Consumer 形式: 委托既有实现, 避免提示文案逻辑分叉 */
    public static void add(Consumer<Component> sink, String translationKey)
    {
        List<Component> lines = new ArrayList<>();
        add(lines, translationKey);
        lines.forEach(sink);
    }
}
