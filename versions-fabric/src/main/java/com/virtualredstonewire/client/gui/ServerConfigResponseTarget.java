package com.virtualredstonewire.client.gui;

import java.util.Map;

/**
 * 服务端配置响应接收目标（主页预检与服务端配置子页共用）。
 */
public interface ServerConfigResponseTarget
{
    /**
     * requestId 为 0 表示服务端主动同步（非本次请求回执）；sync 为 true 表示配置变更广播；
     * allowed 为访问权限；success 为本次操作结果（与权限分离）；errorKey 为出错的配置键。
     */
    void onServerConfigResponse(int requestId, boolean allowed, String message,
                                Map<String, String> values, boolean sync, String errorKey,
                                boolean success);
}
