package com.virtualredstonewire.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.virtualredstonewire.RedstoneDiagnostics;
import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/*
 * /vredtest 红石信号诊断命令（权限 2，OP 可用）
 * 在服务端搭建临时测试装置并逐项断言，输出 [VREDTEST] 日志。
 */
@Mod.EventBusSubscriber(modid = VirtualRedstoneWire.MOD_ID)
public class VRedTestCommand
{
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("vredtest")
            .requires(src -> src.hasPermission(2))
            .executes(VRedTestCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> ctx)
    {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        if (level != null)
        {
            int failures = RedstoneDiagnostics.runTest(level);
            source.sendSuccess(
                () -> Component.literal("VREDTEST finished, assertion failures=" + failures),
                false);
            return failures == 0 ? 1 : 0;
        }
        return 0;
    }
}
