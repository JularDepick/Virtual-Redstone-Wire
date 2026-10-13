package com.virtualredstonewire.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig
{
    /** 操作日志文件分割模式（配置值序列化为小写 total/both/player） */
    public enum OperationLogSplit
    {
        TOTAL,  /* 全部玩家合并写入同一文件*/
        BOTH,   /* 同时写合并文件与按玩家分文件*/
        PLAYER  /* 仅按玩家名称分文件*/
    }

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue maxLinkDistance;
    public static final ForgeConfigSpec.IntValue magnifierRenderDistance;
    public static final ForgeConfigSpec.IntValue networkChangeLogSize;
    public static final ForgeConfigSpec.BooleanValue operationLogEnabled;
    public static final ForgeConfigSpec.EnumValue<OperationLogSplit> operationLogSplit;
    public static final ForgeConfigSpec.ConfigValue<String> operationLogFile;
    public static final ForgeConfigSpec.BooleanValue requestLogEnabled;
    public static final ForgeConfigSpec.ConfigValue<String> requestLogFile;

    static
    {
        BUILDER.push("General");

        maxLinkDistance = BUILDER
            .comment("Maximum distance for a cable link (in blocks)")
            .defineInRange("maxLinkDistance", 256, 1, 1024);

        magnifierRenderDistance = BUILDER
            .comment("Maximum render distance for visualization (in blocks)")
            .defineInRange("magnifierRenderDistance", 512, 64, 1024);

        BUILDER.pop();

        BUILDER.push("Network");

        networkChangeLogSize = BUILDER
            .comment("Capacity of the recent change table for incremental sync (200-1000)")
            .defineInRange("networkChangeLogSize", 200, 200, 1000);

        BUILDER.pop();

        BUILDER.push("Logging");

        operationLogEnabled = BUILDER
            .comment("Log every applied/rejected add/del operation to a per-world text file (for audit, no restore)")
            .define("operationLogEnabled", true);

        operationLogSplit = BUILDER
            .comment("Operation log file split mode: total (one shared file), both (shared + per-player), player (per-player only)")
            .defineEnum("operationLogSplit", OperationLogSplit.TOTAL);

        operationLogFile = BUILDER
            .comment("Operation log file name; with player/both split, the base name gets a _<player> suffix")
            .define("operationLogFile", "Virtual_Redstone_Wire-Operations.log");

        requestLogEnabled = BUILDER
            .comment("Log every received request (raw JSON) with its final status code (debug level)",
                "Status codes: 200=success; 400=dimension/identity mismatch; 404=del target not exists;",
                "409=add already exists / same-tick conflict / batch failure; 422=semantic validation failed")
            .define("requestLogEnabled", false);

        requestLogFile = BUILDER
            .comment("Request log file name (debug level)")
            .define("requestLogFile", "Virtual_Redstone_Wire-Requests.log");

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    /**
     * 把服务端广播来的配置值写入本进程的服务端配置 spec（客户端用于渲染距离与本地预检）。
     */
    public static void applyFromMap(java.util.Map<String, String> values)
    {
        if (values == null || values.isEmpty()) return;
        for (java.util.Map.Entry<String, String> e : values.entrySet())
        {
            String key = e.getKey();
            String raw = e.getValue();
            if (key == null || raw == null) continue;
            try
            {
                switch (key)
                {
                    case "maxLinkDistance":
                        setIfInRange(maxLinkDistance, raw, 1, 1024);
                        break;
                    case "magnifierRenderDistance":
                        setIfInRange(magnifierRenderDistance, raw, 64, 1024);
                        break;
                    case "networkChangeLogSize":
                        setIfInRange(networkChangeLogSize, raw, 200, 1000);
                        break;
                    case "operationLogEnabled":
                        operationLogEnabled.set(Boolean.parseBoolean(raw));
                        break;
                    case "requestLogEnabled":
                        requestLogEnabled.set(Boolean.parseBoolean(raw));
                        break;
                    case "operationLogSplit":
                        operationLogSplit.set(OperationLogSplit.valueOf(raw.toUpperCase(java.util.Locale.ROOT)));
                        break;
                    case "operationLogFile":
                    case "requestLogFile":
                        /* 文件名项不下发（面板不开放配置），忽略*/
                        break;
                    default:
                        break;
                }
            }
            catch (RuntimeException ignored)
            {
                /* 单个键异常不影响其余键*/
            }
        }
    }

    private static void setIfInRange(ForgeConfigSpec.IntValue value, String raw, int min, int max)
    {
        int parsed = Integer.parseInt(raw.trim());
        if (parsed < min || parsed > max) return;
        value.set(parsed);
    }
}
