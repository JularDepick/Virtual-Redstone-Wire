package com.virtualredstonewire.client.gui;

import com.virtualredstonewire.client.ClientCableCache;
import com.virtualredstonewire.data.CableLink;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CableInfoScreen extends Screen
{
    private final BlockPos queryPos;
    private final List<String> outgoingLines;
    private final List<String> incomingLines;

    private int panelLeft;
    private int panelTop;
    private static final int PANEL_WIDTH = 115;
    private static final int HEADER_HEIGHT = 16;
    private static final int LINE_HEIGHT = 11;

    private int closeX, closeY, closeW = 12, closeH = 12;
    private double scrollOffset;
    private int maxScroll;

    public CableInfoScreen(BlockPos pos)
    {
        super(Component.translatable("screen.virtual_redstone_wire.cable_info"));
        this.queryPos = pos.immutable();

        int maxChars = (PANEL_WIDTH - 8) / font.width("W");

        List<String> outRaw = new ArrayList<>();
        List<String> inRaw = new ArrayList<>();
        for (CableLink link : ClientCableCache.getLinks())
        {
            if (link.getFrom().equals(pos))
            {
                outRaw.add("-> [" + link.getTo().getX() + "," + link.getTo().getY()
                    + "," + link.getTo().getZ() + "] " + link.getToFace().getName());
            }
            if (link.getTo().equals(pos))
            {
                inRaw.add("<- [" + link.getFrom().getX() + "," + link.getFrom().getY()
                    + "," + link.getFrom().getZ() + "] " + link.getToFace().getName());
            }
        }
        this.outgoingLines = wrapLines(outRaw, maxChars);
        this.incomingLines = wrapLines(inRaw, maxChars);
    }

    private List<String> wrapLines(List<String> raw, int maxChars)
    {
        List<String> result = new ArrayList<>();
        for (String line : raw)
        {
            if (line.length() <= maxChars)
            {
                result.add(line);
            }
            else
            {
                int pos = 0;
                while (pos < line.length())
                {
                    int end = Math.min(pos + maxChars, line.length());
                    result.add(line.substring(pos, end));
                    pos = end;
                }
            }
        }
        return result;
    }

    @Override
    protected void init()
    {
        int panelHeight = Math.min(height - 40, Math.max(100, height / 2));
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - panelHeight) / 2;
        closeX = panelLeft + PANEL_WIDTH - 18;
        closeY = panelTop + 3;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta)
    {
        int panelBottom = panelTop + panelHeight();
        int contentTop = panelTop + HEADER_HEIGHT + 6;
        int contentBottom = panelBottom - 6;
        int visibleHeight = contentBottom - contentTop;

        int totalContentH = LINE_HEIGHT + 2
            + LINE_HEIGHT + outgoingLines.size() * LINE_HEIGHT
            + 4 + LINE_HEIGHT + incomingLines.size() * LINE_HEIGHT;
        maxScroll = Math.max(0, totalContentH - visibleHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelBottom, 0xE0101010);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + HEADER_HEIGHT, 0xFF2A2A2A);
        graphics.drawString(font,
            Component.translatable("screen.virtual_redstone_wire.cable_info"),
            panelLeft + 7, panelTop + 4, 0xCCCCCC);

        boolean hoveringClose = mouseX >= closeX && mouseX < closeX + closeW
            && mouseY >= closeY && mouseY < closeY + closeH;
        int closeColor = hoveringClose ? 0xFFFF4444 : 0xFF888888;
        graphics.drawString(font, "X", closeX, closeY, closeColor);

        graphics.fill(panelLeft, panelTop + HEADER_HEIGHT, panelLeft + PANEL_WIDTH,
            panelTop + HEADER_HEIGHT + 1, 0xFF444444);

        graphics.enableScissor(panelLeft, contentTop, panelLeft + PANEL_WIDTH, contentBottom);

        int contentLeft = panelLeft + 4;
        int y = contentTop - (int) scrollOffset;

        graphics.drawString(font,
            Component.translatable("screen.virtual_redstone_wire.cable_info.position",
                queryPos.getX(), queryPos.getY(), queryPos.getZ()),
            contentLeft, y, 0x55AAFF);
        y += LINE_HEIGHT + 2;

        graphics.drawString(font,
            Component.translatable("screen.virtual_redstone_wire.cable_info.outgoing",
                outgoingLines.size()),
            contentLeft, y, 0x55FFFF);
        y += LINE_HEIGHT;
        for (String line : outgoingLines)
        {
            graphics.drawString(font, Component.literal(line), contentLeft, y, 0xAAAAAA);
            y += LINE_HEIGHT;
        }
        y += 4;

        graphics.drawString(font,
            Component.translatable("screen.virtual_redstone_wire.cable_info.incoming",
                incomingLines.size()),
            contentLeft, y, 0xFFFF55);
        y += LINE_HEIGHT;
        for (String line : incomingLines)
        {
            graphics.drawString(font, Component.literal(line), contentLeft, y, 0xAAAAAA);
            y += LINE_HEIGHT;
        }

        graphics.disableScissor();

        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + 1, 0xFF666666);
        graphics.fill(panelLeft, panelBottom - 1, panelLeft + PANEL_WIDTH, panelBottom, 0xFF666666);
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF666666);
        graphics.fill(panelLeft + PANEL_WIDTH - 1, panelTop, panelLeft + PANEL_WIDTH, panelBottom, 0xFF666666);
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
        if (mouseX < panelLeft || mouseX > panelLeft + PANEL_WIDTH
            || mouseY < panelTop || mouseY > panelTop + panelHeight())
        {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int panelHeight()
    {
        return Math.min(height - 40, Math.max(100, height / 2));
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
