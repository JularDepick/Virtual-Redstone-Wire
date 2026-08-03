package com.virtualredstonewire.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig
{
    /** 操作日志文件分割模式（配置值序列化为小写 total/both/player） */
    public enum OperationLogSplit
    {
        TOTAL,  // 全部玩家合并写入同一文件
        BOTH,   // 同时写合并文件与按玩家分文件
        PLAYER  // 仅按玩家名称分文件
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
}
