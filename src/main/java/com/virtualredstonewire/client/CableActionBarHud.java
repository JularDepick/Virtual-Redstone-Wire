package com.virtualredstonewire.client;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
     * 提示顶部纵坐标（缩放后像素）：
     * 物品名称提示渲染于 height-59 至 height-50，
     * 本提示以 height-48 为基准再向上偏移一个自身高度（font.lineHeight），
     * 位于物品名称下方且更贴近快捷栏。
     */
    private static final int Y_OFFSET_FROM_BOTTOM = 48;

    private static String text;
    private static long expireAt;

    private CableActionBarHud() {}

    /** 显示飘浮提示 */
    public static void show(String content)
    {
        text = content;
        expireAt = System.currentTimeMillis() + DURATION_MS;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event)
    {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (text == null || System.currentTimeMillis() > expireAt)
        {
            text = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int scaledWidth = event.getWindow().getGuiScaledWidth();
        int scaledHeight = event.getWindow().getGuiScaledHeight();
        int y = scaledHeight - Y_OFFSET_FROM_BOTTOM - font.lineHeight;
        GuiGraphics g = event.getGuiGraphics();
        int x = (scaledWidth - font.width(text)) / 2;
        g.fill(x - 3, y - 2, x + font.width(text) + 3, y + font.lineHeight + 1, 0x66000000);
        g.drawString(font, text, x, y, 0xFFFFFF);
    }
}
