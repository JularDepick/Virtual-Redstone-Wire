package com.virtualredstonewire.client.gui;

/**
 * 当前活动的服务端配置响应目标（主页预检或服务端配置子页）。
 * 打开/关闭配置界面时维护，网络响应回调经此分发。
 */
public final class ConfigScreenTarget
{
    private static ServerConfigResponseTarget active;

    private ConfigScreenTarget() {}

    public static void set(ServerConfigResponseTarget target)
    {
        active = target;
    }

    /** 仅当 active 为目标自身时清空（子页/主页切换互不误清） */
    public static void clear(ServerConfigResponseTarget target)
    {
        if (active == target)
        {
            active = null;
        }
    }

    public static ServerConfigResponseTarget get()
    {
        return active;
    }
}
