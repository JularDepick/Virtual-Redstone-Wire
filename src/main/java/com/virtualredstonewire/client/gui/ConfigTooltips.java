package com.virtualredstonewire.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 配置项悬浮提示组件：行渲染阶段只登记悬停的配置键；
 * 待列表渲染完成后统一绘制，避免提示被后续行覆盖。
 * 提示文本取 config.virtual_redstone_wire.<字段名>.tooltip，
 * 缺失翻译（取值与键路径全等）时不绘制，不回退显示原始键名。
 */
public final class ConfigTooltips
{
    private static final String KEY_PREFIX = "config.virtual_redstone_wire.";

    private String hoveredKey;

    /** 帧开始：清除上一帧的悬停登记，避免残留 */
    public void beginFrame()
    {
        hoveredKey = null;
    }

    /** 行渲染中悬停为真时登记本行配置键 */
    public void markHovered(String configKey)
    {
        hoveredKey = configKey;
    }

    /** 列表渲染完成后绘制：无悬停行或该键无翻译文本时不绘制 */
    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY)
    {
        if (hoveredKey == null) return;
        String keyPath = KEY_PREFIX + hoveredKey + ".tooltip";
        Component text = Component.translatable(keyPath);
        if (keyPath.equals(text.getString())) return;
        guiGraphics.renderTooltip(font, text, mouseX, mouseY);
    }
}
