package com.virtualredstonewire.item;

import com.virtualredstonewire.client.CableClientQueue;
import com.virtualredstonewire.client.ClientCableCache;
import com.virtualredstonewire.config.ClientConfig;
import com.virtualredstonewire.data.CableLink;
import com.virtualredstonewire.network.CableOpPacket;
import com.virtualredstonewire.network.CableProtocol;
import com.virtualredstonewire.util.TooltipLines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
/* 提示行收集改为 Consumer 形式, 并新增 TooltipDisplay 形参*/
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CableCutterItem extends Item
{
    public CableCutterItem(Properties properties)
    {
        super(properties);
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

    /** 右键方块动作: 批量删除以该方块为起点的全部出链 */
    public InteractionResult performRightClick(UseOnContext context)
    {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockPos pos = context.getClickedPos();

        if (level.isClientSide)
        {
            /* 缓存内容不属于当前维度时（换维度守卫最长 1 tick 的窗口）不提交删除，*/
            /* 避免拿他维度缓存的坐标当成本维度的删除目标*/
            if (!ClientCableCache.isCurrentFor(level))
            {
                return InteractionResult.PASS;
            }

            /* 剪刀转换为删除该起点全部出链的单个 del 批量请求，加入任务队列*/
            List<CableOpPacket.Link> batch = new ArrayList<>();
            for (CableLink link : ClientCableCache.getLinks())
            {
                if (link.getFrom().equals(pos))
                {
                    batch.add(new CableOpPacket.Link(
                        link.getFrom().getX(), link.getFrom().getY(), link.getFrom().getZ(),
                        link.getTo().getX(), link.getTo().getY(), link.getTo().getZ(),
                        link.getToFace().getName()));
                }
            }
            if (batch.isEmpty())
            {
                return InteractionResult.PASS;
            }
            if (batch.size() > CableProtocol.BATCH_LIMIT)
            {
                /* 超批量不再静默返回（原实现无任何提示，玩家会以为已经切断）*/
                if (ClientConfig.enableChatFeedback.get())
                {
                    player.displayClientMessage(
                        Component.translatable("message.virtual_redstone_wire.cut_too_many",
                            CableProtocol.BATCH_LIMIT)
                            .withStyle(style -> style.withColor(0xFF5555)), false);
                }
                return InteractionResult.PASS;
            }
            CableClientQueue.enqueue(
                CableOpPacket.del(batch, level.dimension(), player.getName().getString()));

            /* 结果反馈改由服务端确认后的广播驱动（CableClientQueue 的批量汇总提示），*/
            /* 不再在发送前就提示"已切断"（服务端可能拒绝，届时提示会与事实相反）*/
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    /*
     * 适配: appendHoverText 形参换代。
     * 第二形参仍为 Item.TooltipContext, 新增 TooltipDisplay 形参,
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                 TooltipDisplay display, Consumer<Component> tooltipAdder, TooltipFlag flag)
    {
        TooltipLines.add(tooltipAdder, "item.virtual_redstone_wire.cable_cutter.desc");
        TooltipLines.add(tooltipAdder, "item.virtual_redstone_wire.cable_cutter.tip");
    }
}
