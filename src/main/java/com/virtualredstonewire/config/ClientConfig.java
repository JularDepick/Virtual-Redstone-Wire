package com.virtualredstonewire.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig
{
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue enableChatFeedback;

    public static final ForgeConfigSpec.ConfigValue<String> colorInput;
    public static final ForgeConfigSpec.ConfigValue<String> colorOutput;
    public static final ForgeConfigSpec.ConfigValue<String> colorLine;
    public static final ForgeConfigSpec.ConfigValue<String> colorDim;
    public static final ForgeConfigSpec.ConfigValue<String> colorSelected;

    public static final ForgeConfigSpec.IntValue lineWidthPx;

    static
    {
        BUILDER.push("General");

        enableChatFeedback = BUILDER
            .comment("Enable chat message feedback for operations")
            .define("enableChatFeedback", false);

        lineWidthPx = BUILDER
            .comment("Cable line width in pixels (default 2)")
            .defineInRange("lineWidthPx", 2, 1, 32);

        BUILDER.pop();

        BUILDER.push("Colors");
        colorInput = BUILDER
            .comment("Input block outline color (hex #RRGGBB)")
            .define("input", "#0096ff");
        colorOutput = BUILDER
            .comment("Output face outline color (hex #RRGGBB)")
            .define("output", "#ffff63");
        colorLine = BUILDER
            .comment("Cable beam color (hex #RRGGBB)")
            .define("line", "#db4f4f");
        colorDim = BUILDER
            .comment("Selected input dim color (hex #RRGGBB)")
            .define("dim", "#4d66b3");
        colorSelected = BUILDER
            .comment("Unselected link color (hex #RRGGBB)")
            .define("selected", "#4dccff");
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
