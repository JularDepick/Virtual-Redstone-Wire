package com.virtualredstonewire.client.gui;

import java.util.Map;

/**
 * 服务端配置响应接收目标（主页预检与服务端配置子页共用）。
 */
public interface ServerConfigResponseTarget
{
    void onServerConfigResponse(boolean allowed, String message, Map<String, String> values);
}
