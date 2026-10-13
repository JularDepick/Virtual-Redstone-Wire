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
    /** 信号查询节流：同一维度同一目标方块 1 秒内不得重复查询；查询其他方块/维度不受限 */
    private static final long QUERY_COOLDOWN_MS = 1000;
    private static BlockPos lastQueryPos = null;
    private static String lastQueryDimension = null;
    private static long lastQueryTime = 0;

    public CableMagnifierItem(Properties properties)
    {
        super(properties);
    }

    /**
     * 尝试发起信号查询：同一维度同方块在节流期内返回 false；不同方块或不同维度始终放行并记录。
     * 节流键加入所属维度，避免换维度后同坐标被误判为重复查询；
     * force 为 true 时跳过节流（信息面板自身的查询必须发出，否则面板会永久停在等待态）。
     */
    public static boolean tryStartQuery(BlockPos pos, String dimension, boolean force)
    {
        long now = System.currentTimeMillis();
        boolean sameTarget = pos.equals(lastQueryPos)
            && (dimension == null ? lastQueryDimension == null : dimension.equals(lastQueryDimension));
        if (!force && sameTarget && now - lastQueryTime < QUERY_COOLDOWN_MS)
        {
            return false;
        }
        lastQueryPos = pos.immutable();
        lastQueryDimension = dimension;
        lastQueryTime = now;
        return true;
    }

    /**
     * 保留原版的完整判定链(命中方块类型, 主手优先, onItemUseFirst, 方块自身 use 等),
     * 只在这里做"按下沿压制": 长按期间原版每 4 tick 会重新走到这里, 非本次按下的后续调用直接放行给原版。
     */
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        if (!com.virtualredstonewire.client.CableInteractGate.shouldFireOnce()) return InteractionResult.PASS;
        return performRightClick(context);
    }

    /** 右键方块动作: 下蹲打开信息面板 / 未下蹲飘浮显示信号强度 */
    public InteractionResult performRightClick(UseOnContext context)
    {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (level.isClientSide)
        {
            if (player.isCrouching())
            {
                /* 下蹲 + 右键：查看链路信息面板（面板内查询走强制通道，不受交互节流影响）。*/
                /* 经 DistExecutor 进入客户端分支，避免物品类在专用服务器解析客户端 GUI 类*/
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> CableInfoScreenOpener.open(context.getClickedPos()));
                return InteractionResult.SUCCESS;
            }

            /* 未下蹲 + 右键：快捷栏上方飘浮提示目标方块的红石信号强度（同维度同方块 1 秒节流）*/
            if (tryStartQuery(context.getClickedPos(),
                level.dimension().location().toString(), false))
            {
                CableNetworkChannel.sendToServer(
                    new CableInfoRequestPacket(context.getClickedPos(), true,
                        level.dimension().location().toString()));
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
