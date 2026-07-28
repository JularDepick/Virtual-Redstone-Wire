package com.virtualredstonewire.client.gui;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.List;

public class CableInfoScreen extends Screen
{
    private final BlockPos queryPos;
    private List<String> outgoing;
    private List<String> incoming;

    public CableInfoScreen(BlockPos pos)
    {
        super(Component.literal("Cable Connection Info"));
        this.queryPos = pos.immutable();

        // 从客户端缓存获取数据
        Level level = Minecraft.getInstance().level;
        if (level != null)
        {
            CableNetwork network = CableNetworkManager.get(level);
            this.outgoing = network.queryOutgoing(pos);
            this.incoming = network.queryIncoming(pos);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta)
    {
        renderBackground(graphics);
        int left = width / 2 - 120;

        graphics.drawString(font,
            Component.literal("=== Cable Info ==="),
            left, 20, 0xFFFFFF);

        graphics.drawString(font,
            Component.literal("Position: [" + queryPos.getX()
                + ", " + queryPos.getY() + ", " + queryPos.getZ() + "]"),
            left, 35, 0x55AAFF);

        // 出边
        graphics.drawString(font,
            Component.literal("Outgoing (" + (outgoing != null ? outgoing.size() : 0) + "):"),
            left, 55, 0x55FFFF);
        if (outgoing != null)
        {
            int y = 70;
            for (String line : outgoing)
            {
                graphics.drawString(font, Component.literal("  " + line), left, y, 0xAAAAAA);
                y += 12;
            }
        }

        // 入边
        int startY = 70 + (outgoing != null ? Math.max(outgoing.size() * 12 + 5, 5) : 5);
        graphics.drawString(font,
            Component.literal("Incoming (" + (incoming != null ? incoming.size() : 0) + "):"),
            left, startY, 0xFFFF55);
        if (incoming != null)
        {
            int y = startY + 15;
            for (String line : incoming)
            {
                graphics.drawString(font, Component.literal("  " + line), left, y, 0xAAAAAA);
                y += 12;
            }
        }

        graphics.drawString(font,
            Component.literal("Press ESC to close"),
            left, height - 30, 0x666666);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
