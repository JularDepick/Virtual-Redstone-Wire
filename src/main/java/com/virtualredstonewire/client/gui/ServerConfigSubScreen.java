package com.virtualredstonewire.client.gui;

import com.virtualredstonewire.network.CableNetworkChannel;
import com.virtualredstonewire.network.CableServerConfigRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端配置文件子页（virtual_redstone_wire-server.toml 条目）：
 * 两列无边框表格——第一列显示名（语言翻译，超长自动换行），
 * 第二列 CycleButton/EditBox；EditBox 无确定按钮，修改暂存，
 * 点击底部"完成"统一提交生效（布尔/枚举点击即生效）。
 * 非拥有者仅显示拒绝原因，不渲染修改控件。
 */
public class ServerConfigSubScreen extends Screen implements ServerConfigResponseTarget
{
    private static final int BG_COLOR = 0x99000000;
    private static final int TITLE_Y = 16;
    private static final int LIST_Y0 = 40;
    private static final int ITEM_HEIGHT = 34;
    private static final int EDGE_PADDING = 0;
    private static final int MID_GAP = 8;
    private static final int DONE_HEIGHT = 20;

    /** 数值配置项范围（键名 -> [最小值, 最大值]），面板提交时自动缩减到边界 */
    private static final Map<String, int[]> NUMERIC_RANGES = Map.of(
        "maxLinkDistance", new int[] {1, 1024},
        "magnifierRenderDistance", new int[] {64, 1024},
        "networkChangeLogSize", new int[] {200, 1000});

    private final Screen parent;
    private boolean requested;
    private boolean allowed;
    private String message = "";
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, String> pending = new LinkedHashMap<>();
    private final ConfigTooltips tooltips = new ConfigTooltips();
    private ConfigList list;

    public ServerConfigSubScreen(Screen parent)
    {
        super(Component.translatable("screen.virtual_redstone_wire.config.server"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        ConfigScreenTarget.set(this);
        if (!requested)
        {
            requested = true;
            CableNetworkChannel.sendToServer(
                new CableServerConfigRequestPacket(
                    CableServerConfigRequestPacket.ACTION_GET, "", ""));
        }
        rebuild();
    }

    @Override
    public void onClose()
    {
        ConfigScreenTarget.clear(this);
        if (this.parent instanceof ServerConfigResponseTarget)
        {
            ConfigScreenTarget.set((ServerConfigResponseTarget) this.parent);
        }
        this.minecraft.setScreen(this.parent);
    }

    /** 黑色半透明背景 */
    @Override
    public void renderBackground(GuiGraphics guiGraphics)
    {
        guiGraphics.fill(0, 0, this.width, this.height, BG_COLOR);
    }

    private void rebuild()
    {
        clearWidgets();
        list = new ConfigList(this.width, this.height, LIST_Y0,
            this.height - DONE_HEIGHT - 12, ITEM_HEIGHT);
        if (allowed)
        {
            for (Map.Entry<String, String> entry : values.entrySet())
            {
                String key = entry.getKey();
                String value = entry.getValue();
                if (isHiddenKey(key))
                {
                    continue; // 文件名不允许在面板中配置（配置文件手动管理）
                }
                if (isBooleanKey(key))
                {
                    list.addRow(new ServerBoolRow(key, Boolean.parseBoolean(value)));
                }
                else if (isEnumKey(key))
                {
                    list.addRow(new SplitSliderRow(key, value));
                }
                else
                {
                    list.addRow(new ServerTextRow(key, value));
                }
            }
        }
        addRenderableWidget(list);
        int doneWidth = this.font.width(
            Component.translatable("gui.done").getString()) + 40;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),
            b -> submitPendingAndClose())
            .bounds(this.width / 2 - doneWidth / 2,
                this.height - DONE_HEIGHT - 10, doneWidth, DONE_HEIGHT)
            .build());
    }

    private static boolean isHiddenKey(String key)
    {
        return "operationLogFile".equals(key) || "requestLogFile".equals(key);
    }

    private static boolean isBooleanKey(String key)
    {
        return "operationLogEnabled".equals(key) || "requestLogEnabled".equals(key);
    }

    private static boolean isEnumKey(String key)
    {
        return "operationLogSplit".equals(key);
    }

    private void sendSet(String key, String value)
    {
        CableNetworkChannel.sendToServer(
            new CableServerConfigRequestPacket(
                CableServerConfigRequestPacket.ACTION_SET, key, value));
    }

    /** 底部完成：提交全部暂存修改（数值项超范围自动缩减到边界并提示）后返回主页 */
    private void submitPendingAndClose()
    {
        Minecraft mc = Minecraft.getInstance();
        for (Map.Entry<String, String> entry : pending.entrySet())
        {
            String key = entry.getKey();
            String value = entry.getValue();
            int[] range = NUMERIC_RANGES.get(key);
            if (range != null)
            {
                try
                {
                    int parsed = Integer.parseInt(value.trim());
                    int clamped = Math.max(range[0], Math.min(range[1], parsed));
                    if (clamped != parsed && mc.player != null)
                    {
                        mc.player.sendSystemMessage(Component.translatable(
                            "message.virtual_redstone_wire.config_clamped",
                            ConfigLabels.name(key), range[0], range[1], clamped)
                            .withStyle(style -> style.withColor(0xFFAA00)));
                    }
                    value = String.valueOf(clamped);
                }
                catch (NumberFormatException e)
                {
                    continue; // 非数字输入：跳过不提交
                }
            }
            sendSet(key, value);
        }
        pending.clear();
        onClose();
    }

    /** 服务端返回消息本地化显示（config_range 键按出错配置项的范围参数补齐） */
    private String localizedMessage()
    {
        if ("message.virtual_redstone_wire.config_range".equals(message))
        {
            String key = values.keySet().stream().findFirst().orElse("");
            int[] range = NUMERIC_RANGES.get(key);
            if (range != null)
            {
                return Component.translatable(message, range[0], range[1]).getString();
            }
        }
        return Component.translatable(message).getString();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        tooltips.beginFrame();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
        if (requested && !allowed)
        {
            guiGraphics.drawCenteredString(this.font, localizedMessage(),
                this.width / 2, 120, 0xAAAAAA);
        }
        else if (requested && values.isEmpty())
        {
            guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.virtual_redstone_wire.config.loading").getString(),
                this.width / 2, 120, 0xAAAAAA);
        }
        tooltips.render(guiGraphics, this.font, mouseX, mouseY);
    }

    @Override
    public void onServerConfigResponse(boolean allowed, String message, Map<String, String> values)
    {
        this.allowed = allowed;
        this.message = message == null ? "" : message;
        // 仅全量响应或权限状态变化时重建行；单项响应（set 回执）只更新值，
        // 避免重建列表导致滚动位置与输入暂存被重置
        boolean reload = !allowed || this.values.isEmpty();
        if (values != null && !values.isEmpty())
        {
            reload |= values.size() > 1;
            this.values.putAll(values);
        }
        if (reload && this.minecraft != null && this.minecraft.screen == this)
        {
            rebuild();
        }
    }

    // ---------- 滚动条目 ----------

    private final class ConfigList extends ContainerObjectSelectionList<Row>
    {
        ConfigList(int width, int height, int y0, int y1, int itemHeight)
        {
            super(ServerConfigSubScreen.this.minecraft, width, height, y0, y1, itemHeight);
        }

        void addRow(Row row)
        {
            addEntry(row);
        }

        /** 滚动条紧邻列表（屏幕）右边界 */
        @Override
        protected int getScrollbarPosition()
        {
            return this.x1 - 6;
        }
    }

    private abstract class Row extends ContainerObjectSelectionList.Entry<Row>
    {
        protected final String key;
        protected final String name;
        protected final List<AbstractWidget> widgets = new ArrayList<>();

        Row(String key)
        {
            this.key = key;
            this.name = ConfigLabels.name(key);
        }

        protected void addWidget(AbstractWidget widget)
        {
            widgets.add(widget);
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables()
        {
            return widgets;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width,
                           int height, int mouseX, int mouseY, boolean hovered, float partialTick)
        {
            if (hovered)
            {
                ServerConfigSubScreen.this.tooltips.markHovered(key);
            }
            drawLabel(guiGraphics, left, width, top);
            positionWidgets(left, width, top);
            for (AbstractWidget widget : widgets)
            {
                widget.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        /** 第一列：翻译名，水平居左、单元格内垂直居中，超长自动换行（左列占一半） */
        private void drawLabel(GuiGraphics guiGraphics, int left, int width, int top)
        {
            int colX0 = left + EDGE_PADDING;
            int colX1 = left + width / 2 - MID_GAP;
            List<FormattedCharSequence> lines = ServerConfigSubScreen.this.font.split(
                Component.literal(name), colX1 - colX0);
            int totalHeight = lines.size() * ServerConfigSubScreen.this.font.lineHeight;
            int y = top + (ITEM_HEIGHT - totalHeight) / 2;
            for (FormattedCharSequence line : lines)
            {
                guiGraphics.drawString(ServerConfigSubScreen.this.font, line, colX0, y, 0xFFFFFF);
                y += ServerConfigSubScreen.this.font.lineHeight;
            }
        }

        protected abstract void positionWidgets(int left, int width, int top);
    }

    private final class ServerBoolRow extends Row
    {
        ServerBoolRow(String key, boolean current)
        {
            super(key);
            int width = Math.max(
                ServerConfigSubScreen.this.font.width(
                    Component.translatable("options.on").getString()),
                ServerConfigSubScreen.this.font.width(
                    Component.translatable("options.off").getString())) + 16;
            CycleButton<Boolean> cycle = CycleButton.builder(
                (Boolean b) -> Component.translatable(b ? "options.on" : "options.off"))
                .withValues(true, false)
                .withInitialValue(current)
                .displayOnlyValue()
                .create(0, 0, width, 20, Component.literal(name),
                    (btn, b) -> sendSet(key, String.valueOf(b)));
            addWidget(cycle);
        }

        @Override
        protected void positionWidgets(int left, int width, int top)
        {
            AbstractWidget slider = widgets.get(0);
            slider.setPosition(left + width - EDGE_PADDING - slider.getWidth(),
                top + (ITEM_HEIGHT - 20) / 2);
        }
    }

    /** 操作日志分割档位条（total/both/player 三档，档位文本语言键本地化，拖动显示、释放提交） */
    private final class SplitSliderRow extends Row
    {
        private static final String[] KEYS = {
            "screen.virtual_redstone_wire.config.split.total",
            "screen.virtual_redstone_wire.config.split.both",
            "screen.virtual_redstone_wire.config.split.player"};
        private static final String[] VALUES = {"total", "both", "player"};

        SplitSliderRow(String key, String current)
        {
            super(key);
            int initial = Math.max(0, java.util.Arrays.asList(VALUES).indexOf(current));
            int width = 0;
            for (String k : KEYS)
            {
                width = Math.max(width, ServerConfigSubScreen.this.font.width(
                    Component.translatable(k).getString()));
            }
            width += 16;
            AbstractSliderButton slider = new AbstractSliderButton(0, 0, width, 20,
                Component.translatable(KEYS[initial]), initial / (double) (KEYS.length - 1))
            {
                @Override
                protected void updateMessage()
                {
                    setMessage(Component.translatable(KEYS[getIndex()]));
                }

                @Override
                protected void applyValue()
                {
                    updateMessage();
                }

                @Override
                public void onRelease(double mouseX, double mouseY)
                {
                    super.onRelease(mouseX, mouseY);
                    sendSet(key, VALUES[getIndex()]);
                }

                private int getIndex()
                {
                    return (int) Math.round(this.value * (KEYS.length - 1));
                }
            };
            addWidget(slider);
        }

        @Override
        protected void positionWidgets(int left, int width, int top)
        {
            widgets.get(0).setPosition(left + width - EDGE_PADDING - widgets.get(0).getWidth(),
                top + (ITEM_HEIGHT - 20) / 2);
        }
    }

    /** 文本/数值条目：EditBox 修改暂存，无确定按钮，底部完成统一提交 */
    private final class ServerTextRow extends Row
    {
        ServerTextRow(String key, String current)
        {
            super(key);
            int width = Math.max(80, ServerConfigSubScreen.this.font.width(current) + 24);
            EditBox box = new EditBox(ServerConfigSubScreen.this.font,
                0, 0, width, 16, Component.literal(name));
            box.setValue(current);
            box.setMaxLength(128);
            box.setFilter(text -> text.matches("[0-9]*"));
            box.setResponder(text -> pending.put(key, text));
            addWidget(box);
        }

        @Override
        protected void positionWidgets(int left, int width, int top)
        {
            AbstractWidget box = widgets.get(0);
            box.setPosition(left + width - EDGE_PADDING - box.getWidth(),
                top + (ITEM_HEIGHT - 16) / 2);
        }
    }
}
