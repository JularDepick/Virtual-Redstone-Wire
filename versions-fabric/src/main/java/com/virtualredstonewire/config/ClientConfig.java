package com.virtualredstonewire.config;

/*
 * 客户端配置：字段名、默认值与取值范围与 Forge 侧基线一一对应。
 * 与 ServerConfig 一样改用同包自建的 ConfigSpec，平台无关的调用方无需改动。
 */
public class ClientConfig extends ConfigSpec
{
    public static final ClientConfig INSTANCE = new ClientConfig();

    public static final BoolEntry enableChatFeedback =
        INSTANCE.add(new BoolEntry("General", "enableChatFeedback", false));

    public static final IntEntry undoHistorySize =
        INSTANCE.add(new IntEntry("General", "undoHistorySize", 20, 10, 100));

    public static final BoolEntry undoRedoFeedback =
        INSTANCE.add(new BoolEntry("General", "undoRedoFeedback", true));

    @Override
    public String fileName()
    {
        return "virtual_redstone_wire-client.toml";
    }
}
