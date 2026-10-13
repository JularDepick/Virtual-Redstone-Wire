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
 *
 * 1.21.2 适配: Entity/Player 的 sendSystemMessage 被移除,
 * 本类一处聊天栏反馈改用 displayClientMessage(Component, false)。
 * v1.0.0：未连接时不发送请求（避免标题界面打开时 NPE）；请求带回执标识并在超时后有限重试；
 * 权限（allowed）与本次操作结果（success）分离，失败回执不再清空面板；
 * 服务端配置变更广播（sync）会刷新非暂存项的显示值。
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
    private static final long GET_TIMEOUT_MS = 3000L;
    private static final int GET_MAX_ATTEMPTS = 3;

    /** 数值配置项范围（键名 -> [最小值, 最大值]），面板提交时自动缩减到边界 */
    private static final Map<String, int[]> NUMERIC_RANGES = Map.of(
        "maxLinkDistance", new int[] {1, 1024},
        "magnifierRenderDistance", new int[] {64, 1024},
        "networkChangeLogSize", new int[] {200, 1000});

    private final Screen parent;
    private boolean requested;
    private boolean allowed;
    private boolean lastSuccess = true;
    private String message = "";
    private String errorKey = null;
    private boolean requestPending;
    private int attempts;
    private long requestTime;
    private final List<Integer> sentRequestIds = new ArrayList<>();
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
            sendGet();
        }
        rebuild();
    }

    /** v1.0.0：仅在存在服务器连接时发送请求 */
    private void sendGet()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        CableServerConfigRequestPacket packet =
            new CableServerConfigRequestPacket(CableServerConfigRequestPacket.ACTION_GET, "", "");
        trackRequest(packet);
        CableNetworkChannel.sendToServer(packet);
    }

    private void trackRequest(CableServerConfigRequestPacket packet)
    {
        sentRequestIds.add(packet.getRequestId());
        if (sentRequestIds.size() > 16) sentRequestIds.remove(0);
        requestPending = true;
        attempts++;
        requestTime = System.currentTimeMillis();
    }

    /** v1.0.0：请求超时有限重试，避免一次丢包导致子页永久停留在"加载中" */
    private void maybeRetryGet()
    {
        if (!requestPending) return;
        if (System.currentTimeMillis() - requestTime < GET_TIMEOUT_MS) return;
        if (attempts >= GET_MAX_ATTEMPTS)
        {
            requestPending = false;
            return;
        }
        sendGet();
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
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
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
        CableServerConfigRequestPacket packet = new CableServerConfigRequestPacket(
            CableServerConfigRequestPacket.ACTION_SET, key, value);
        trackRequest(packet);
        CableNetworkChannel.sendToServer(packet);
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
                        mc.player.displayClientMessage(Component.translatable(
                            "message.virtual_redstone_wire.config_clamped",
                            ConfigLabels.name(key), range[0], range[1], clamped)
                            .withStyle(style -> style.withColor(0xFFAA00)), false);
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
            // v1.0.0：按服务端回执的出错键取范围（不再取 values 的第一个键）
            int[] range = errorKey == null ? null : NUMERIC_RANGES.get(errorKey);
            if (range != null)
            {
                return Component.translatable(message, range[0], range[1]).getString();
            }
        }
        if (message == null || message.isEmpty()) return "";
        return Component.translatable(message).getString();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick)
    {
        maybeRetryGet();
        tooltips.beginFrame();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
        if (requested && !allowed)
        {
            guiGraphics.drawCenteredString(this.font, localizedMessage(),
                this.width / 2, 120, 0xAAAAAA);
        }
        else if (requested && !lastSuccess)
        {
            guiGraphics.drawCenteredString(this.font, localizedMessage(),
                this.width / 2, 120, 0xFFAA00);
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
    public void onServerConfigResponse(int requestId, boolean allowed, String message,
                                       Map<String, String> values, boolean sync, String errorKey,
                                       boolean success)
    {
        if (sync)
        {
            // v1.0.0：服务端配置变更广播：更新非暂存项；无暂存修改时重建行以反映新值
            if (values != null)
            {
                for (Map.Entry<String, String> e : values.entrySet())
                {
                    if (!pending.containsKey(e.getKey())) this.values.put(e.getKey(), e.getValue());
                }
            }
            if (pending.isEmpty() && this.minecraft != null && this.minecraft.screen == this)
            {
                rebuild();
            }
            return;
        }
        if (requestId != 0 && !sentRequestIds.contains(requestId)) return; // 非本次请求的回执
        requestPending = false;
        this.allowed = allowed;
        this.lastSuccess = success;
        this.message = message == null ? "" : message;
        this.errorKey = errorKey;
        if (values != null && !values.isEmpty())
        {
            this.values.putAll(values);
        }
        boolean reload = !allowed || this.values.isEmpty()
            || (values != null && values.size() > 1);
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
            super(ServerConfigSubScreen.this.minecraft, width, y1 - y0, y0, itemHeight);
        }

        void addRow(Row row)
        {
            addEntry(row);
        }

        /** 滚动条紧邻列表（屏幕）右边界 */
        @Override
        protected int getScrollbarPosition()
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
                Component.literal(name), Math.max(8, colX1 - colX0));
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
