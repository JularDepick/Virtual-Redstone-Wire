package com.virtualredstonewire.util;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * 输出面名称的本地化显示工具。
 */
public final class FaceNames
{
    private static final String KEY_PREFIX = "screen.virtual_redstone_wire.face.";

    private FaceNames() {}

    /** 本地化面名；face 为 null 时返回空串 */
    public static String of(Direction face)
    {
        if (face == null) return "";
        String key = KEY_PREFIX + face.getName();
        String text = Component.translatable(key).getString();
        return text.equals(key) ? face.getName() : text;
    }

    /** 本地化面名；按原版面名字符串解析，未知名返回空串 */
    public static String of(String faceName)
    {
        if (faceName == null) return "";
        return of(Direction.byName(faceName));
    }
}
