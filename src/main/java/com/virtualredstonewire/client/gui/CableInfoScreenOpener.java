package com.virtualredstonewire.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * 客户端 GUI 打开辅助类。
 */
public final class CableInfoScreenOpener
{
    private CableInfoScreenOpener() {}

    public static void open(BlockPos pos)
    {
        Minecraft.getInstance().setScreen(new CableInfoScreen(pos));
    }
}
