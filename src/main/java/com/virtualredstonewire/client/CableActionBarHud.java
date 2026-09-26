package com.virtualredstonewire.client;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.client.gui.CableUndoRedoHistoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 快捷栏上方飘浮提示（放大镜信号强度等）。
 * 自定义渲染：贴近快捷栏、位于物品名称提示下方并与之间隔相接（不重叠）。
 * 显示时长 2 秒后自动消失。
 */
@Mod.EventBusSubscriber(modid = VirtualRedstoneWire.MOD_ID, value = Dist.CLIENT)
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

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event)
    {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        // 历史窗口页打开时不渲染本提示，避免遮挡窗口页
        if (Minecraft.getInstance().screen instanceof CableUndoRedoHistoryScreen) return;
        if (text == null || System.currentTimeMillis() > expireAt)
        {
            text = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int scaledWidth = event.getWindow().getGuiScaledWidth();
        int scaledHeight = event.getWindow().getGuiScaledHeight();
        int y = scaledHeight - TEXT_TOP_FROM_BOTTOM;
        GuiGraphics g = event.getGuiGraphics();
        int x = (scaledWidth - font.width(text)) / 2;
        g.fill(x - 3, y - 2, x + font.width(text) + 3, y + font.lineHeight + 1, 0x66000000);
        g.drawString(font, text, x, y, 0xFFFFFF);
    }
}
