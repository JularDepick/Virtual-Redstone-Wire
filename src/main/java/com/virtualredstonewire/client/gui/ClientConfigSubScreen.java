package com.virtualredstonewire.client.gui;

import com.virtualredstonewire.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端配置文件子页（virtual_redstone_wire-client.toml 条目）：
 * 两列无边框表格——第一列显示名（语言翻译，超长自动换行），
 * 第二列 CycleButton 开关 / EditBox 数值输入，修改即时生效并保存。
 */
public class ClientConfigSubScreen extends Screen
{
    private static final int BG_COLOR = 0x99000000;
    private static final int TITLE_Y = 16;
    private static final int LIST_Y0 = 40;
    private static final int ITEM_HEIGHT = 34;
    private static final int EDGE_PADDING = 0;
    private static final int MID_GAP = 8;
    private static final int DONE_HEIGHT = 20;

    private final Screen parent;
    private ConfigList list;

    public ClientConfigSubScreen(Screen parent)
    {
        super(Component.translatable("screen.virtual_redstone_wire.config.client"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        rebuild();
    }

    @Override
    public void onClose()
    {
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
        list.addRow(new ClientBoolRow(ClientConfig.enableChatFeedback));
        list.addRow(new ClientBoolRow(ClientConfig.undoRedoFeedback));
        list.addRow(new ClientIntRow(ClientConfig.undoHistorySize, 10, 100));
        addRenderableWidget(list);
        int doneWidth = this.font.width(
            Component.translatable("gui.done").getString()) + 40;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(this.width / 2 - doneWidth / 2,
                this.height - DONE_HEIGHT - 10, doneWidth, DONE_HEIGHT)
            .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
    }

    // ---------- 滚动条目 ----------

    private final class ConfigList extends ContainerObjectSelectionList<Row>
    {
        ConfigList(int width, int height, int y0, int y1, int itemHeight)
        {
            super(ClientConfigSubScreen.this.minecraft, width, height, y0, y1, itemHeight);
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
        protected final String name;
        protected final List<AbstractWidget> widgets = new ArrayList<>();

        Row(String key)
        {
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
            List<FormattedCharSequence> lines = ClientConfigSubScreen.this.font.split(
                Component.literal(name), colX1 - colX0);
            int totalHeight = lines.size() * ClientConfigSubScreen.this.font.lineHeight;
            int y = top + (ITEM_HEIGHT - totalHeight) / 2;
            for (FormattedCharSequence line : lines)
            {
                guiGraphics.drawString(ClientConfigSubScreen.this.font, line, colX0, y, 0xFFFFFF);
                y += ClientConfigSubScreen.this.font.lineHeight;
            }
        }

        protected abstract void positionWidgets(int left, int width, int top);
    }

    private final class ClientBoolRow extends Row
    {
        ClientBoolRow(ForgeConfigSpec.BooleanValue value)
        {
            super(lastPathKey(value));
            int width = Math.max(
                ClientConfigSubScreen.this.font.width(
                    Component.translatable("options.on").getString()),
                ClientConfigSubScreen.this.font.width(
                    Component.translatable("options.off").getString())) + 16;
            CycleButton<Boolean> cycle = CycleButton.builder(
                (Boolean b) -> Component.translatable(b ? "options.on" : "options.off"))
                .withValues(true, false)
                .withInitialValue(value.get())
                .displayOnlyValue()
                .create(0, 0, width, 20, Component.literal(name),
                    (btn, b) -> {
                        value.set(b);
                        ClientConfig.SPEC.save();
                    });
            addWidget(cycle);
        }

        @Override
        protected void positionWidgets(int left, int width, int top)
        {
            AbstractWidget widget = widgets.get(0);
            widget.setPosition(left + width - EDGE_PADDING - widget.getWidth(),
                top + (ITEM_HEIGHT - 20) / 2);
        }
    }

    /** 数值条目：EditBox 数字输入，合法输入即时生效并保存，超范围自动缩减到边界 */
    private final class ClientIntRow extends Row
    {
        ClientIntRow(ForgeConfigSpec.IntValue value, int min, int max)
        {
            super(lastPathKey(value));
            EditBox box = new EditBox(ClientConfigSubScreen.this.font,
                0, 0, 64, 16, Component.literal(name));
            box.setValue(String.valueOf(value.get()));
            box.setMaxLength(8);
            box.setFilter(text -> text.matches("[0-9]*"));
            box.setResponder(text ->
            {
                if (text == null || text.trim().isEmpty()) return;
                try
                {
                    int parsed = Integer.parseInt(text.trim());
                    int clamped = Math.max(min, Math.min(max, parsed));
                    value.set(clamped);
                    ClientConfig.SPEC.save();
                }
                catch (NumberFormatException e)
                {
                    // 非法输入忽略，保持当前值
                }
            });
            addWidget(box);
        }

        @Override
        protected void positionWidgets(int left, int width, int top)
        {
            AbstractWidget widget = widgets.get(0);
            widget.setPosition(left + width - EDGE_PADDING - widget.getWidth(),
                top + (ITEM_HEIGHT - 16) / 2);
        }
    }

    private static String lastPathKey(ForgeConfigSpec.ConfigValue<?> value)
    {
        List<String> path = value.getPath();
        return path.get(path.size() - 1);
    }
}
