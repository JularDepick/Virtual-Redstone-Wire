package com.virtualredstonewire.client.gui;

import com.virtualredstonewire.client.ClientCableCache;
import com.virtualredstonewire.data.CableLink;
import com.virtualredstonewire.network.CableInfoRequestPacket;
import com.virtualredstonewire.network.CableNetworkChannel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CableInfoScreen extends Screen
{
    private static final int MIN_WIDTH = 115;
    private static final int MAX_WIDTH = 230;
    private static final int HEADER_HEIGHT = 16;
    private static final int LINE_HEIGHT = 11;

    private static volatile CableInfoScreen activeScreen;

    private final BlockPos queryPos;
    private final List<String> lines = new ArrayList<>();
    private final List<Integer> outEntryLines = new ArrayList<>();
    private final List<Integer> inEntryLines = new ArrayList<>();
    private int signalLineIndex = -1;
    private int signal = -1; // -1 = 等待服务端返回
    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int closeX, closeY, closeW = 12, closeH = 12;
    private double scrollOffset;
    private int maxScroll;

    public CableInfoScreen(BlockPos pos)
    {
        super(Component.translatable("screen.virtual_redstone_wire.cable_info"));
        this.queryPos = pos.immutable();

        lines.add(Component.translatable(
            "screen.virtual_redstone_wire.cable_info.position",
            pos.getX(), pos.getY(), pos.getZ()).getString());

        signalLineIndex = lines.size();
        String pending = Component.translatable(
            "screen.virtual_redstone_wire.cable_info.pending").getString();
        lines.add(Component.translatable(
            "screen.virtual_redstone_wire.cable_info.signal", pending).getString());

        String none = Component.translatable(
            "screen.virtual_redstone_wire.cable_info.none").getString();
        String arrowOut = Component.translatable(
            "screen.virtual_redstone_wire.cable_info.arrow_out").getString();
        String arrowIn = Component.translatable(
            "screen.virtual_redstone_wire.cable_info.arrow_in").getString();

        List<String> out = new ArrayList<>();
        List<String> in = new ArrayList<>();
        for (CableLink link : ClientCableCache.getLinks())
        {
            if (link.getFrom().equals(pos))
            {
                out.add(arrowOut + " [" + link.getTo().getX() + "," + link.getTo().getY()
                    + "," + link.getTo().getZ() + "] " + link.getToFace().getName());
            }
            if (link.getTo().equals(pos))
            {
                in.add(arrowIn + " [" + link.getFrom().getX() + "," + link.getFrom().getY()
                    + "," + link.getFrom().getZ() + "] " + link.getToFace().getName());
            }
        }
        if (out.isEmpty())
        {
            lines.add(Component.translatable(
                "screen.virtual_redstone_wire.cable_info.outgoing", none).getString());
        }
        else
        {
            lines.add(Component.translatable(
                "screen.virtual_redstone_wire.cable_info.outgoing", out.size()).getString());
            int base = lines.size();
            for (int i = 0; i < out.size(); i++)
            {
                outEntryLines.add(base + i);
            }
            lines.addAll(out);
        }
        if (in.isEmpty())
        {
            lines.add(Component.translatable(
                "screen.virtual_redstone_wire.cable_info.incoming", none).getString());
        }
        else
        {
            lines.add(Component.translatable(
                "screen.virtual_redstone_wire.cable_info.incoming", in.size()).getString());
            int base = lines.size();
            for (int i = 0; i < in.size(); i++)
            {
                inEntryLines.add(base + i);
            }
            lines.addAll(in);
        }

        activeScreen = this;

        // 请求服务端查询该方块当前的红石信号强度（含虚拟链路信号）
        CableNetworkChannel.sendToServer(new CableInfoRequestPacket(queryPos));
    }

    public static CableInfoScreen getActiveScreen()
    {
        return activeScreen;
    }

    public BlockPos getQueryPos()
    {
        return queryPos;
    }

    public void onSignalReceived(int received)
    {
        this.signal = received;
        if (signalLineIndex >= 0 && signalLineIndex < lines.size())
        {
            lines.set(signalLineIndex, Component.translatable(
                "screen.virtual_redstone_wire.cable_info.signal", received).getString());
        }
    }

    @Override
    public void onClose()
    {
        if (activeScreen == this)
        {
            activeScreen = null;
        }
        super.onClose();
    }

    @Override
    protected void init()
    {
        int maxTextW = 0;
        for (String line : lines)
        {
            int w = font.width(line);
            if (w > maxTextW) maxTextW = w;
        }
        panelWidth = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, maxTextW));

        int panelHeight = Math.min(height - 10, Math.max(60, height / 2));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        closeX = panelLeft + panelWidth - 14;
        closeY = panelTop + 3;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta)
    {
        int panelBottom = panelTop + panelHeight();
        int contentTop = panelTop + HEADER_HEIGHT + 4;
        int contentBottom = panelBottom - 2;
        int visibleHeight = contentBottom - contentTop;

        int totalContentH = lines.size() * LINE_HEIGHT;
        maxScroll = Math.max(0, totalContentH - visibleHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelBottom, 0xE0101010);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + HEADER_HEIGHT, 0xFF2A2A2A);
        graphics.drawString(font,
            Component.translatable("screen.virtual_redstone_wire.cable_info"),
            panelLeft + 4, panelTop + 4, 0xCCCCCC);

        boolean hoveringClose = mouseX >= closeX && mouseX < closeX + closeW
            && mouseY >= closeY && mouseY < closeY + closeH;
        int closeColor = hoveringClose ? 0xFFFF4444 : 0xFF888888;
        graphics.drawString(font, "X", closeX, closeY, closeColor);

        graphics.fill(panelLeft, panelTop + HEADER_HEIGHT, panelLeft + panelWidth,
            panelTop + HEADER_HEIGHT + 1, 0xFF444444);

        graphics.enableScissor(panelLeft, contentTop, panelLeft + panelWidth, contentBottom);

        int y = contentTop - (int) scrollOffset;
        for (int i = 0; i < lines.size(); i++)
        {
            String line = lines.get(i);
            int color = 0x888888;
            if (i == 0) color = 0x55AAFF;
            else if (i == signalLineIndex) color = signal > 0 ? 0x55FF55 : 0x888888;
            else if (outEntryLines.contains(i)) color = 0x55FFFF;
            else if (inEntryLines.contains(i)) color = 0xFFFF55;
            graphics.drawString(font, Component.literal(line), panelLeft + 2, y, color);
            y += LINE_HEIGHT;
        }

        graphics.disableScissor();

        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 1, 0xFF666666);
        graphics.fill(panelLeft, panelBottom - 1, panelLeft + panelWidth, panelBottom, 0xFF666666);
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF666666);
        graphics.fill(panelLeft + panelWidth - 1, panelTop, panelLeft + panelWidth, panelBottom, 0xFF666666);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY)
    {
        scrollOffset = Math.max(0, Math.min(scrollOffset - scrollY * LINE_HEIGHT * 2, maxScroll));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (mouseX >= closeX && mouseX < closeX + closeW
            && mouseY >= closeY && mouseY < closeY + closeH)
        {
            this.onClose();
            return true;
        }
        if (mouseX < panelLeft || mouseX > panelLeft + panelWidth
            || mouseY < panelTop || mouseY > panelTop + panelHeight())
        {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int panelHeight()
    {
        return Math.min(height - 10, Math.max(60, height / 2));
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
