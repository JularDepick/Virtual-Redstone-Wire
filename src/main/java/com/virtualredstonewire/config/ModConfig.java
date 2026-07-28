package com.virtualredstonewire.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ModConfig
{
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue maxLinkDistance;
    public static final ForgeConfigSpec.IntValue magnifierRenderDistance;
    public static final ForgeConfigSpec.BooleanValue enableChatFeedback;

    static
    {
        BUILDER.push("General");

        maxLinkDistance = BUILDER
            .comment("Maximum distance for a cable link (in blocks)")
            .defineInRange("maxLinkDistance", 256, 1, 1024);

        magnifierRenderDistance = BUILDER
            .comment("Maximum render distance for magnifier visualization (in blocks)")
            .defineInRange("magnifierRenderDistance", 512, 64, 1024);

        enableChatFeedback = BUILDER
            .comment("Enable chat message feedback for operations")
            .define("enableChatFeedback", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
