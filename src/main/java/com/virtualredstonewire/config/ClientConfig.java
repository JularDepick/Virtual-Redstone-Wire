package com.virtualredstonewire.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig
{
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue enableChatFeedback;

    static
    {
        BUILDER.push("General");

        enableChatFeedback = BUILDER
            .comment("Enable chat message feedback for operations")
            .define("enableChatFeedback", false);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
