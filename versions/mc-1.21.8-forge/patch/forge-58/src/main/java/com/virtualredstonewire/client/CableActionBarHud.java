package com.virtualredstonewire.client;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.client.gui.CableUndoRedoHistoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 快捷栏上方飘浮提示（放大镜信号强度等），支持组件内嵌颜色。
 */
public class CableActionBarHud
{
    private static final long DURATION_MS = 2000;
    /**
     * 提示文本行顶部距屏幕底部的像素数（缩放后像素）。
     */
    private static final int TEXT_TOP_FROM_BOTTOM = 32;

    private static Component text;
    private static long expireAt;

    private CableActionBarHud() {}

    public static void show(Component content)
    {
        text = content;
        expireAt = System.currentTimeMillis() + DURATION_MS;
    }

    /**
     * 由 MixinGui 在 Gui#render 末尾调用。
     */
    public static void renderHud(GuiGraphics g)
    {
        /* 历史窗口页打开时不渲染本提示，避免遮挡窗口页*/
        if (Minecraft.getInstance().screen instanceof CableUndoRedoHistoryScreen) return;
        if (text == null || System.currentTimeMillis() > expireAt)
        {
            text = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int scaledWidth = mc.getWindow().getGuiScaledWidth();
        int scaledHeight = mc.getWindow().getGuiScaledHeight();
        int y = scaledHeight - TEXT_TOP_FROM_BOTTOM;
        int x = (scaledWidth - font.width(text)) / 2;
        g.fill(x - 3, y - 2, x + font.width(text) + 3, y + font.lineHeight + 1, 0x66000000);
        g.drawString(font, text, x, y, 0xFFFFFF);
    }
}
