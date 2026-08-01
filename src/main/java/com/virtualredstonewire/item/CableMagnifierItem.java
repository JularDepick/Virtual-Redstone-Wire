package com.virtualredstonewire.item;

import com.virtualredstonewire.client.gui.CableInfoScreenOpener;
import com.virtualredstonewire.network.CableInfoRequestPacket;
import com.virtualredstonewire.network.CableNetworkChannel;
import com.virtualredstonewire.util.TooltipLines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CableMagnifierItem extends Item
{
    /** 信号查询节流：发起查询后 1 秒内不得再次发起（无论服务端返回什么） */
    private static final long QUERY_COOLDOWN_MS = 1000;
    private static long lastQueryTime = 0;

    public CableMagnifierItem(Properties properties)
    {
        super(properties);
    }

    /** 尝试发起信号查询：通过节流则记录时间并返回 true，否则返回 false */
    public static boolean tryStartQuery()
    {
        long now = System.currentTimeMillis();
        if (now - lastQueryTime < QUERY_COOLDOWN_MS)
        {
            return false;
        }
        lastQueryTime = now;
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (level.isClientSide)
        {
            if (player.isCrouching())
            {
                // 下蹲 + 右键：查看链路信息面板（原有逻辑，面板内查询同样受节流）
                CableInfoScreenOpener.open(context.getClickedPos());
                return InteractionResult.SUCCESS;
            }

            // 未下蹲 + 右键：快捷栏上方飘浮提示目标方块的红石信号强度（受 1 秒节流）
            if (tryStartQuery())
            {
                CableNetworkChannel.sendToServer(
                    new CableInfoRequestPacket(context.getClickedPos(), true));
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag)
    {
        TooltipLines.add(tooltip, "item.virtual_redstone_wire.cable_magnifier.desc");
        TooltipLines.add(tooltip, "item.virtual_redstone_wire.cable_magnifier.tip");
    }
}
