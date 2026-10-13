package com.virtualredstonewire.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.ArrayList;

/**
 * 配置项悬浮提示组件：行渲染阶段只登记悬停的配置键；
 */
public final class ConfigTooltips
{
    private static final String KEY_PREFIX = "config.virtual_redstone_wire.";
    /** 悬停后提示的显示时长（毫秒），超时自动隐藏 */
    private static final long SHOW_DURATION_MS = 3000;
    /** 提示文本换行宽度上限（像素），语言键内的换行符优先 */
    private static final int MAX_WIDTH = 240;

    private String frameKey;
    private String activeKey;
    private long activeStart;

    public void beginFrame()
    {
        frameKey = null;
    }

    /** 行渲染中悬停为真时登记本行配置键 */
    public void markHovered(String configKey)
    {
        frameKey = configKey;
    }

    /** 列表渲染完成后绘制：离开列表、超过显示时长或该键无翻译文本时不绘制 */
    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY)
    {
        if (frameKey == null)
        {
            activeKey = null;
            return;
        }
        if (!frameKey.equals(activeKey))
        {
            activeKey = frameKey;
            activeStart = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - activeStart >= SHOW_DURATION_MS) return;

        String keyPath = KEY_PREFIX + frameKey + ".tooltip";
        Component text = Component.translatable(keyPath);
        if (keyPath.equals(text.getString())) return;

        List<FormattedCharSequence> lines = font.split(text, MAX_WIDTH);
        /*
         * renderTooltip 收 List<ClientTooltipComponent>, 需逐行包装;
         * 第 5 形参(定位器)不可传 null: 该方法无条件调用 positionTooltip, 传 null 在真正显示提示时抛 NPE,
         * 第 6 形参(纹理)可传 null, TooltipRenderUtil.getBackgroundSprite 显式判空回退.
         */
        List<ClientTooltipComponent> components = new ArrayList<>();
        for (FormattedCharSequence line : lines)
        {
            components.add(ClientTooltipComponent.create(line));
        }
        guiGraphics.renderTooltip(font, components, mouseX, mouseY,
            DefaultTooltipPositioner.INSTANCE, null);
    }
}
