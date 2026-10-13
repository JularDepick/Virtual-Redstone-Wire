package com.virtualredstonewire.client.gui;

import com.virtualredstonewire.network.CableNetworkChannel;
import com.virtualredstonewire.network.CableServerConfigRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模组配置主页（Mods 菜单 -> Config 按钮）：黑色半透明背景，
 * 未连接服务器时不再发送请求（原先在标题界面打开会因连接为 null 抛 NPE）；
 */
public class ClientConfigScreen extends Screen implements ServerConfigResponseTarget
{
    private static final int BG_COLOR = 0x99000000;
    private static final int TITLE_Y = 16;
    private static final int LIST_Y0 = 48;
    private static final int ITEM_HEIGHT = 34;
    private static final int EDGE_PADDING = 0;
    private static final int MID_GAP = 8;
    private static final int DONE_HEIGHT = 20;
    private static final long GET_TIMEOUT_MS = 3000L;
    private static final int GET_MAX_ATTEMPTS = 3;

    private final Screen parent;
    private boolean serverRequested;
    private boolean serverVisible;
    private boolean requestPending;
    private int attempts;
    private long requestTime;
    private final List<Integer> sentRequestIds = new ArrayList<>();

    private ConfigList list;

    public ClientConfigScreen(Screen parent)
    {
        super(Component.translatable("screen.virtual_redstone_wire.config"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        ConfigScreenTarget.set(this);
        if (!serverRequested)
        {
            serverRequested = true;
            sendGet();
        }
        rebuild();
    }

    /** 仅在存在服务器连接时发送预检请求（标题界面打开配置界面时连接为 null） */
    private void sendGet()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        CableServerConfigRequestPacket packet =
            new CableServerConfigRequestPacket(CableServerConfigRequestPacket.ACTION_GET, "", "");
        sentRequestIds.add(packet.getRequestId());
        if (sentRequestIds.size() > 8) sentRequestIds.remove(0);
        requestPending = true;
        attempts++;
        requestTime = System.currentTimeMillis();
        CableNetworkChannel.sendToServer(packet);
    }

    /** 请求超时有限重试，避免预检回执丢失后服务端条目永久不显示 */
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
        list.addRow(new FileRow("virtual_redstone_wire-client.toml",
            () -> this.minecraft.setScreen(new ClientConfigSubScreen(this))));
        if (serverVisible)
        {
            list.addRow(new FileRow("virtual_redstone_wire-server.toml",
                () -> this.minecraft.setScreen(new ServerConfigSubScreen(this))));
        }
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
        maybeRetryGet();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
    }

    @Override
    public void onServerConfigResponse(int requestId, boolean allowed, String message,
                                       Map<String, String> values, boolean sync, String errorKey,
                                       boolean success)
    {
        if (sync) return; /* 主动同步只刷新本地 spec，主页条目无需变化*/
        if (requestId != 0 && !sentRequestIds.contains(requestId)) return; /* 非本次请求的回执*/
        this.requestPending = false;
        this.serverVisible = allowed;
        if (this.minecraft != null && this.minecraft.screen == this)
        {
            rebuild();
        }
    }

    private final class ConfigList extends ContainerObjectSelectionList<FileRow>
    {
        ConfigList(int width, int height, int y0, int y1, int itemHeight)
        {
            super(ClientConfigScreen.this.minecraft, width, y1 - y0, y0, itemHeight);
        }

        void addRow(FileRow row)
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

    private final class FileRow extends ContainerObjectSelectionList.Entry<FileRow>
    {
        private final String fileName;
        private final Button goButton;

        FileRow(String fileName, Runnable onOpen)
        {
            this.fileName = fileName;
            int width = ClientConfigScreen.this.font.width(
                Component.translatable("screen.virtual_redstone_wire.config.open").getString()) + 16;
            this.goButton = Button.builder(
                Component.translatable("screen.virtual_redstone_wire.config.open"),
                b -> onOpen.run())
                .bounds(0, 0, width, 18)
                .build();
        }

        @Override
        public List<? extends GuiEventListener> children()
        {
            return List.of(goButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables()
        {
            return List.of(goButton);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width,
                           int height, int mouseX, int mouseY, boolean hovered, float partialTick)
        {
            drawLabel(guiGraphics, left, width, top);
            goButton.setPosition(left + width - EDGE_PADDING - goButton.getWidth(),
                top + (ITEM_HEIGHT - 18) / 2);
            goButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        /** 第一列：文件名，水平居左、单元格内垂直居中，超长自动换行（左列占一半） */
        private void drawLabel(GuiGraphics guiGraphics, int left, int width, int top)
        {
            int colX0 = left + EDGE_PADDING;
            int colX1 = left + width / 2 - MID_GAP;
            List<FormattedCharSequence> lines = ClientConfigScreen.this.font.split(
                Component.literal(fileName), Math.max(8, colX1 - colX0));
            int totalHeight = lines.size() * ClientConfigScreen.this.font.lineHeight;
            int y = top + (ITEM_HEIGHT - totalHeight) / 2;
            for (FormattedCharSequence line : lines)
            {
                guiGraphics.drawString(ClientConfigScreen.this.font, line, colX0, y, 0xFFFFFF);
                y += ClientConfigScreen.this.font.lineHeight;
            }
        }
    }
}
