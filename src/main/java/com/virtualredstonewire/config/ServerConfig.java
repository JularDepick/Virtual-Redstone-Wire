package com.virtualredstonewire.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig
{
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue maxLinkDistance;
    public static final ForgeConfigSpec.IntValue magnifierRenderDistance;

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
        SPEC = BUILDER.build();
    }
}
