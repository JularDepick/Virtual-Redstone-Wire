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
// 1.21.5 起提示行收集改为 Consumer 形式, 并新增 TooltipDisplay 形参
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

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
     * v1.0.0：节流键加入所属维度，避免换维度后同坐标被误判为重复查询；
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
                // 下蹲 + 右键：查看链路信息面板（面板内查询走强制通道，不受交互节流影响）。
                // 本分支已由 level.isClientSide 守卫，客户端类只在执行到此处时才被链接，
                // 因此不需要加载器提供的侧判定包装
                CableInfoScreenOpener.open(context.getClickedPos());
                return InteractionResult.SUCCESS;
            }

            // 未下蹲 + 右键：快捷栏上方飘浮提示目标方块的红石信号强度（同维度同方块 1 秒节流）
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

    /*
     * 1.21.5 适配: appendHoverText 形参换代。
     * 第二形参仍为 Item.TooltipContext, 新增 TooltipDisplay 形参,
     * 提示行的收集由 List<Component> 改为 Consumer<Component>,
     * 因此 TooltipLines 增补 Consumer 形式的重载。
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 TooltipDisplay display, Consumer<Component> tooltipAdder, TooltipFlag flag)
    {
        TooltipLines.add(tooltipAdder, "item.virtual_redstone_wire.cable_magnifier.desc");
        TooltipLines.add(tooltipAdder, "item.virtual_redstone_wire.cable_magnifier.tip");
    }
}
