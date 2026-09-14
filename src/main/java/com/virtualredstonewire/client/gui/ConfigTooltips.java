package com.virtualredstonewire.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 配置项悬浮提示组件：行渲染阶段只登记悬停的配置键；
 * 待列表渲染完成后统一绘制，避免提示被后续行覆盖。
 * 提示文本取 config.virtual_redstone_wire.<字段名>.tooltip，
 * 缺失翻译（取值与键路径全等）时不绘制，不回退显示原始键名。
 * 提示自悬停起显示固定时长后自动隐藏，移出列表后再次悬停可重新显示。
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

    /** 帧开始：清除本帧的悬停登记 */
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
        guiGraphics.renderTooltip(font, lines, mouseX, mouseY);
    }
}
