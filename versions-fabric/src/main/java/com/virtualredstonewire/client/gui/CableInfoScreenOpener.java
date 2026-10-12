package com.virtualredstonewire.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * 客户端 GUI 打开辅助类。
 * 刻意不标注 @OnlyIn(Dist.CLIENT)：本类只通过 DistExecutor 在 CLIENT 分支调用，
 * 专用服务器永远不会加载它；若标注 @OnlyIn 反而会在被意外加载时报 dist 错误。
 * 将 Screen 引用隔离在此类中，避免 CableMagnifierItem（服务端也会加载的物品类）
 * 的字节码直接引用客户端类导致 DEDICATED_SERVER 崩溃。
 */
public final class CableInfoScreenOpener
{
    private CableInfoScreenOpener() {}

    public static void open(BlockPos pos)
    {
        Minecraft.getInstance().setScreen(new CableInfoScreen(pos));
    }
}
