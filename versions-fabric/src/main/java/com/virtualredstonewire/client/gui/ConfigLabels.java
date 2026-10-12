package com.virtualredstonewire.client.gui;

import net.minecraft.network.chat.Component;

/**
 * 配置项显示名：优先语言键（config.virtual_redstone_wire.<字段名>），
 * 未找到翻译（如英文版）时直接回退为原配置字段名称。
 */
public final class ConfigLabels
{
    private ConfigLabels() {}

    public static String name(String key)
    {
        String keyPath = "config.virtual_redstone_wire." + key;
        String text = Component.translatable(keyPath).getString();
        return text.equals(keyPath) ? key : text;
    }
}
