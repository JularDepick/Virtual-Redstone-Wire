package com.virtualredstonewire.client.gui;

import com.virtualredstonewire.client.CableUndoRedoManager;
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
import com.virtualredstonewire.config.ConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端配置文件子页（virtual_redstone_wire-client.toml 条目）：
 * 并且上限调小时立即收缩撤销/重做双栈。
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
    private final ConfigTooltips tooltips = new ConfigTooltips();
    private final List<ClientIntRow> intRows = new ArrayList<>();
    private boolean configDirty;
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
        /* 数值项在离开界面时统一落盘（原先每次按键都同步写文件）*/
        if (configDirty)
        {
            configDirty = false;
            ClientConfig.INSTANCE.save();
        }
        this.minecraft.setScreen(this.parent);
    }

    /** 黑色半透明背景 */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        guiGraphics.fill(0, 0, this.width, this.height, BG_COLOR);
    }

    private void rebuild()
    {
        clearWidgets();
        intRows.clear();
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
        tooltips.beginFrame();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
        tooltips.render(guiGraphics, this.font, mouseX, mouseY);
        /* 越界输入把输入框文本回写为钳制后的值（在 responder 之外纠正，避免递归）*/
        for (ClientIntRow row : intRows)
        {
            row.clampDisplay();
        }
    }

    private final class ConfigList extends ContainerObjectSelectionList<Row>
    {
        ConfigList(int width, int height, int y0, int y1, int itemHeight)
        {
            super(ClientConfigSubScreen.this.minecraft, width, y1 - y0, y0, itemHeight);
        }

        void addRow(Row row)
        {
            addEntry(row);
        }

        /** 滚动条紧邻列表（屏幕）右边界 */
        @Override
        protected int scrollBarX()
        {
            return this.getRight() - 6;
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
                ClientConfigSubScreen.this.tooltips.markHovered(key);
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
            List<FormattedCharSequence> lines = ClientConfigSubScreen.this.font.split(
                Component.literal(name), Math.max(8, colX1 - colX0));
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
        ClientBoolRow(ConfigSpec.BoolEntry value)
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
                        ClientConfig.INSTANCE.save();
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

    /** 数值条目：EditBox 数字输入，合法输入即时生效（钳制到边界），离开界面时统一保存 */
    private final class ClientIntRow extends Row
    {
        private final ConfigSpec.IntEntry value;
        private final int min;
        private final int max;
        private final EditBox box;

        ClientIntRow(ConfigSpec.IntEntry value, int min, int max)
        {
            super(lastPathKey(value));
            this.value = value;
            this.min = min;
            this.max = max;
            this.box = new EditBox(ClientConfigSubScreen.this.font,
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
                    configDirty = true;
                    if ("undoHistorySize".equals(key))
                    {
                        /* 上限调小时立即收缩双栈*/
                        CableUndoRedoManager.trimToLimit();
                    }
                }
                catch (NumberFormatException e)
                {
                    /* 非法输入忽略，保持当前值*/
                }
            });
            addWidget(box);
            intRows.add(this);
        }

        /** 输入框文本超出范围时回写为钳制值，保证显示与实际生效值一致 */
        void clampDisplay()
        {
            String text = box.getValue();
            if (text == null || text.trim().isEmpty()) return;
            try
            {
                int parsed = Integer.parseInt(text.trim());
                int clamped = Math.max(min, Math.min(max, parsed));
                if (clamped != parsed)
                {
                    String s = String.valueOf(clamped);
                    if (!s.equals(text))
                    {
                        box.setValue(s);
                    }
                }
            }
            catch (NumberFormatException ignored)
            {
                /* 非法输入交给 responder 处理*/
            }
        }

        @Override
        protected void positionWidgets(int left, int width, int top)
        {
            AbstractWidget widget = widgets.get(0);
            widget.setPosition(left + width - EDGE_PADDING - widget.getWidth(),
                top + (ITEM_HEIGHT - 16) / 2);
        }
    }

    private static String lastPathKey(ConfigSpec.Entry<?> value)
    {
        return value.key();
    }
}
