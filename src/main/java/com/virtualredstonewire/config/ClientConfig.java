package com.virtualredstonewire.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig
{
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue enableChatFeedback;
    public static final ForgeConfigSpec.IntValue undoHistorySize;
    public static final ForgeConfigSpec.BooleanValue undoRedoFeedback;

    static
    {
        BUILDER.push("General");

        enableChatFeedback = BUILDER
            .comment("Enable chat message feedback for operations")
            .define("enableChatFeedback", false);

        undoHistorySize = BUILDER
            .comment("Undo/redo history stack size limit (shared by undo and redo stacks)")
            .defineInRange("undoHistorySize", 20, 10, 100);

        undoRedoFeedback = BUILDER
            .comment("Enable undo/redo feedback messages (empty/success/failure)")
            .define("undoRedoFeedback", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
