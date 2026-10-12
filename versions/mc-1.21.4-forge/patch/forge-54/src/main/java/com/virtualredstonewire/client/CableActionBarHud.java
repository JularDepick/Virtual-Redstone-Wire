package com.virtualredstonewire.client;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.client.gui.CableUndoRedoHistoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 快捷栏上方飘浮提示（放大镜信号强度等）。
 * 自定义渲染：贴近快捷栏、位于物品名称提示下方并与之间隔相接（不重叠）。
 * 显示时长 2 秒后自动消失。
 */
public class CableActionBarHud
{
    private static final long DURATION_MS = 2000;
    /**
     * 提示文本行顶部距屏幕底部的像素数（缩放后像素）。
     * 原版物品名称高亮框下沿随模式变化：生存模式为 height-48，创造模式为 height-35；
     * 快捷栏顶端为 height-22。本提示背景框取 [height-34, height-22)，
     * 即紧贴快捷栏上方，且不与两种模式下的物品名称框重叠。
     */
    private static final int TEXT_TOP_FROM_BOTTOM = 32;

    private static Component text;
    private static long expireAt;

    private CableActionBarHud() {}

    /** 显示飘浮提示（支持组件内嵌颜色） */
    public static void show(Component content)
    {
        text = content;
        expireAt = System.currentTimeMillis() + DURATION_MS;
    }

    /**
     * 由 MixinGui 在 Gui#render 末尾调用。
     * 1.20.5 起 Forge 移除了 RenderGuiOverlayEvent(Forge 转用原版 LayeredDraw 分层渲染),
     * 绘制内容与位置与 1.20.1-1.20.4 上的实现保持一致; 唯一的差异是绘制时机从
     * "快捷栏覆盖层之后"变为"整个 GUI 渲染之后", 因此本提示必定位于其他 GUI 元素之上。
     */
    public static void renderHud(GuiGraphics g)
    {
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
