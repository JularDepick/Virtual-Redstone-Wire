package com.virtualredstonewire.config;

import java.util.Locale;
import java.util.Map;

/*
 * 服务端配置：字段名、默认值与取值范围与 Forge 侧基线一一对应。
 * Forge 侧用 ForgeConfigSpec 描述并写 TOML；Fabric 侧改用同包自建的 ConfigSpec，
 * 因此平台无关的调用方（ServerConfig.maxLinkDistance.get() 这类）一行都不用改。
 */
public class ServerConfig extends ConfigSpec
{
    /** 操作日志文件分割模式（配置值序列化为小写 total/both/player） */
    public enum OperationLogSplit
    {
        TOTAL,  // 全部玩家合并写入同一文件
        BOTH,   // 同时写合并文件与按玩家分文件
        PLAYER  // 仅按玩家名称分文件
    }

    public static final ServerConfig INSTANCE = new ServerConfig();

    public static final IntEntry maxLinkDistance =
        INSTANCE.add(new IntEntry("General", "maxLinkDistance", 256, 1, 1024));

    public static final IntEntry magnifierRenderDistance =
        INSTANCE.add(new IntEntry("General", "magnifierRenderDistance", 512, 64, 1024));

    public static final IntEntry networkChangeLogSize =
        INSTANCE.add(new IntEntry("Network", "networkChangeLogSize", 200, 200, 1000));

    public static final BoolEntry operationLogEnabled =
        INSTANCE.add(new BoolEntry("Logging", "operationLogEnabled", true));

    public static final EnumEntry<OperationLogSplit> operationLogSplit =
        INSTANCE.add(new EnumEntry<>("Logging", "operationLogSplit", OperationLogSplit.TOTAL));

    public static final StringEntry operationLogFile =
        INSTANCE.add(new StringEntry("Logging", "operationLogFile",
            "Virtual_Redstone_Wire-Operations.log"));

    public static final BoolEntry requestLogEnabled =
        INSTANCE.add(new BoolEntry("Logging", "requestLogEnabled", false));

    public static final StringEntry requestLogFile =
        INSTANCE.add(new StringEntry("Logging", "requestLogFile",
            "Virtual_Redstone_Wire-Requests.log"));

    @Override
    public String fileName()
    {
        return "virtual_redstone_wire-server.toml";
    }

    /**
     * v1.0.0：把服务端广播来的配置值写入本进程的服务端配置（客户端用于渲染距离与本地预检）。
     * 只改内存值，不写客户端本地文件。未知键、非法数值与越界值一律忽略。
     */
    public static void applyFromMap(Map<String, String> values)
    {
        if (values == null || values.isEmpty()) return;
        for (Map.Entry<String, String> e : values.entrySet())
        {
            String key = e.getKey();
            String raw = e.getValue();
            if (key == null || raw == null) continue;
            try
            {
                switch (key)
                {
                    case "maxLinkDistance":
                        maxLinkDistance.parse(raw);
                        break;
                    case "magnifierRenderDistance":
                        magnifierRenderDistance.parse(raw);
                        break;
                    case "networkChangeLogSize":
                        networkChangeLogSize.parse(raw);
                        break;
                    case "operationLogEnabled":
                        operationLogEnabled.set(Boolean.parseBoolean(raw));
                        break;
                    case "requestLogEnabled":
                        requestLogEnabled.set(Boolean.parseBoolean(raw));
                        break;
                    case "operationLogSplit":
                        operationLogSplit.set(OperationLogSplit.valueOf(raw.toUpperCase(Locale.ROOT)));
                        break;
                    case "operationLogFile":
                    case "requestLogFile":
                        // 文件名项不下发（面板不开放配置），忽略
                        break;
                    default:
                        break;
                }
            }
            catch (RuntimeException ignored)
            {
                // 单个键异常不影响其余键
            }
        }
    }
}
