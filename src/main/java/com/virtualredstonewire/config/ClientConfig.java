package com.virtualredstonewire.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ClientConfig
{
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue enableChatFeedback;

    public static final ForgeConfigSpec.DoubleValue colorInputR;
    public static final ForgeConfigSpec.DoubleValue colorInputG;
    public static final ForgeConfigSpec.DoubleValue colorInputB;
    public static final ForgeConfigSpec.DoubleValue colorInputA;

    public static final ForgeConfigSpec.DoubleValue colorOutputR;
    public static final ForgeConfigSpec.DoubleValue colorOutputG;
    public static final ForgeConfigSpec.DoubleValue colorOutputB;
    public static final ForgeConfigSpec.DoubleValue colorOutputA;

    public static final ForgeConfigSpec.DoubleValue colorLineR;
    public static final ForgeConfigSpec.DoubleValue colorLineG;
    public static final ForgeConfigSpec.DoubleValue colorLineB;
    public static final ForgeConfigSpec.DoubleValue colorLineA;

    public static final ForgeConfigSpec.DoubleValue colorDimR;
    public static final ForgeConfigSpec.DoubleValue colorDimG;
    public static final ForgeConfigSpec.DoubleValue colorDimB;
    public static final ForgeConfigSpec.DoubleValue colorDimA;

    public static final ForgeConfigSpec.DoubleValue colorSelectedR;
    public static final ForgeConfigSpec.DoubleValue colorSelectedG;
    public static final ForgeConfigSpec.DoubleValue colorSelectedB;
    public static final ForgeConfigSpec.DoubleValue colorSelectedA;

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

        BUILDER.push("Colors.Input");
        colorInputR = BUILDER.defineInRange("r", 0.0, 0.0, 1.0);
        colorInputG = BUILDER.defineInRange("g", 0.59, 0.0, 1.0);
        colorInputB = BUILDER.defineInRange("b", 1.0, 0.0, 1.0);
        colorInputA = BUILDER.defineInRange("a", 1.0, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("Colors.Output");
        colorOutputR = BUILDER.defineInRange("r", 1.0, 0.0, 1.0);
        colorOutputG = BUILDER.defineInRange("g", 1.0, 0.0, 1.0);
        colorOutputB = BUILDER.defineInRange("b", 0.39, 0.0, 1.0);
        colorOutputA = BUILDER.defineInRange("a", 1.0, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("Colors.Line");
        colorLineR = BUILDER.defineInRange("r", 0.86, 0.0, 1.0);
        colorLineG = BUILDER.defineInRange("g", 0.31, 0.0, 1.0);
        colorLineB = BUILDER.defineInRange("b", 0.31, 0.0, 1.0);
        colorLineA = BUILDER.defineInRange("a", 1.0, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("Colors.Dim");
        colorDimR = BUILDER.defineInRange("r", 0.3, 0.0, 1.0);
        colorDimG = BUILDER.defineInRange("g", 0.4, 0.0, 1.0);
        colorDimB = BUILDER.defineInRange("b", 0.7, 0.0, 1.0);
        colorDimA = BUILDER.defineInRange("a", 1.0, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("Colors.Selected");
        colorSelectedR = BUILDER.defineInRange("r", 0.3, 0.0, 1.0);
        colorSelectedG = BUILDER.defineInRange("g", 0.8, 0.0, 1.0);
        colorSelectedB = BUILDER.defineInRange("b", 1.0, 0.0, 1.0);
        colorSelectedA = BUILDER.defineInRange("a", 1.0, 0.0, 1.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
