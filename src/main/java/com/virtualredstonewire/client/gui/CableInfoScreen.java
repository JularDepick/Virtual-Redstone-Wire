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
    private final List<String> outgoing;
    private final List<String> incoming;

    private int panelLeft;
    private int panelTop;
    private static final int PANEL_WIDTH = 240;
    private static final int HEADER_HEIGHT = 16;
    private static final int LINE_HEIGHT = 11;

    private int closeX, closeY, closeW = 12, closeH = 12;

    public CableInfoScreen(BlockPos pos)
    {
        super(Component.translatable("screen.virtual_redstone_wire.cable_info"));
        this.queryPos = pos.immutable();

        List<String> out = new ArrayList<>();
        List<String> in = new ArrayList<>();

        for (CableLink link : ClientCableCache.getLinks())
        {
            if (link.getFrom().equals(pos))
            {
                out.add("-> [" + link.getTo().getX() + "," + link.getTo().getY()
                    + "," + link.getTo().getZ() + "] " + link.getToFace().getName());
            }
            if (link.getTo().equals(pos))
            {
                in.add("<- [" + link.getFrom().getX() + "," + link.getFrom().getY()
                    + "," + link.getFrom().getZ() + "] " + link.getToFace().getName());
            }
        }
        this.outgoing = out;
        this.incoming = in;
    }

    @Override
    protected void init()
    {
        int contentLines = 1
            + 1 + outgoing.size()
            + 1 + incoming.size();
        int panelHeight = HEADER_HEIGHT + 8 + contentLines * LINE_HEIGHT + 12;
        panelHeight = Math.max(panelHeight, 100);
        panelHeight = Math.min(panelHeight, height - 40);

        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - panelHeight) / 2;

        closeX = panelLeft + PANEL_WIDTH - 18;
        closeY = panelTop + 3;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta)
    {
        int panelBottom = panelTop + panelHeight();

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

        int y = panelTop + HEADER_HEIGHT + 6;
        int contentLeft = panelLeft + 8;

        graphics.drawString(font,
            Component.translatable("screen.virtual_redstone_wire.cable_info.position",
                queryPos.getX(), queryPos.getY(), queryPos.getZ()),
            contentLeft, y, 0x55AAFF);
        y += LINE_HEIGHT + 2;

        graphics.drawString(font,
            Component.translatable("screen.virtual_redstone_wire.cable_info.outgoing",
                outgoing.size()),
            contentLeft, y, 0x55FFFF);
        y += LINE_HEIGHT;
        for (String line : outgoing)
        {
            if (y > panelBottom - 16) break;
            graphics.drawString(font, Component.literal("  " + line), contentLeft, y, 0xAAAAAA);
            y += LINE_HEIGHT;
        }
        y += 4;

        graphics.drawString(font,
            Component.translatable("screen.virtual_redstone_wire.cable_info.incoming",
                incoming.size()),
            contentLeft, y, 0xFFFF55);
        y += LINE_HEIGHT;
        for (String line : incoming)
        {
            if (y > panelBottom - 16) break;
            graphics.drawString(font, Component.literal("  " + line), contentLeft, y, 0xAAAAAA);
            y += LINE_HEIGHT;
        }

        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + 1, 0xFF666666);
        graphics.fill(panelLeft, panelBottom - 1, panelLeft + PANEL_WIDTH, panelBottom, 0xFF666666);
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF666666);
        graphics.fill(panelLeft + PANEL_WIDTH - 1, panelTop, panelLeft + PANEL_WIDTH, panelBottom, 0xFF666666);
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
        int contentLines = 1 + 1 + outgoing.size() + 1 + incoming.size();
        int h = HEADER_HEIGHT + 8 + contentLines * LINE_HEIGHT + 12;
        h = Math.max(h, 100);
        h = Math.min(h, height - 40);
        return h;
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
