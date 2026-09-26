package com.virtualredstonewire.item;

import com.virtualredstonewire.client.CableClientQueue;
import com.virtualredstonewire.client.ClientCableCache;
import com.virtualredstonewire.config.ClientConfig;
import com.virtualredstonewire.config.ServerConfig;
import com.virtualredstonewire.network.CableOpPacket;
import com.virtualredstonewire.util.TooltipLines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VirtualCableItem extends Item
{
    /** 起点选中态（v1.0.0：带所属维度，避免换维度后残留被渲染或误用） */
    private record Selection(String dimension, BlockPos pos) {}

    private static final Map<UUID, Selection> SELECTED_INPUTS = new HashMap<>();

    public VirtualCableItem(Properties properties)
    {
        super(properties);
    }

    /** 当前维度下的选中起点；选中态属于其他维度时返回 null（不渲染也不参与交互） */
    public static BlockPos getSelectedInput(Player player)
    {
        Selection selection = SELECTED_INPUTS.get(player.getUUID());
        if (selection == null) return null;
        if (!selection.dimension().equals(player.level().dimension().location().toString())) return null;
        return selection.pos();
    }

    public static void setSelectedInput(Player player, BlockPos pos)
    {
        SELECTED_INPUTS.put(player.getUUID(),
            new Selection(player.level().dimension().location().toString(), pos.immutable()));
    }

    public static void clearSelectedInput(Player player)
    {
        SELECTED_INPUTS.remove(player.getUUID());
    }

    /** 清空全部选中态（客户端登出/断线时调用，防跨会话残留） */
    public static void clearAllSelectedInputs()
    {
        SELECTED_INPUTS.clear();
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

    /** 右键方块动作: 选中输入端起点 / 输出端建链 / 再次右键同一方块取消选中 */
    public InteractionResult performRightClick(UseOnContext context)
    {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        if (level.isClientSide)
        {
            BlockPos sel = getSelectedInput(player);

            if (sel == null)
            {
                setSelectedInput(player, clickedPos);
                if (ClientConfig.enableChatFeedback.get())
                {
                    player.sendSystemMessage(
                        Component.translatable("message.virtual_redstone_wire.selected_input",
                            clickedPos.getX(), clickedPos.getY(), clickedPos.getZ())
                            .withStyle(style -> style.withColor(0xFFFF55)));
                }
                return InteractionResult.SUCCESS;
            }

            if (sel.equals(clickedPos))
            {
                clearSelectedInput(player);
                if (ClientConfig.enableChatFeedback.get())
                {
                    player.sendSystemMessage(
                        Component.translatable("message.virtual_redstone_wire.cancelled")
                            .withStyle(style -> style.withColor(0xFFFFFF)));
                }
                return InteractionResult.PASS;
            }

            // v0.3.0：本地 422 类预检（客户端先行校验，服务端兜底）
            double maxDist = ServerConfig.maxLinkDistance.get();
            if (Math.sqrt(sel.distSqr(clickedPos)) > maxDist)
            {
                if (ClientConfig.enableChatFeedback.get())
                {
                    player.sendSystemMessage(
                        Component.translatable("message.virtual_redstone_wire.too_far", maxDist)
                            .withStyle(style -> style.withColor(0xFFAA00)));
                }
                return InteractionResult.PASS;
            }
            if (level.isEmptyBlock(sel))
            {
                return InteractionResult.PASS;
            }

            // v1.0.0：缓存内容不属于当前维度时（换维度守卫最长 1 tick 的窗口）不提交操作，
            // 避免用他维度缓存把 add 判成 del（或反之）
            if (!ClientCableCache.isCurrentFor(level))
            {
                return InteractionResult.PASS;
            }

            // 意图转换：按本地缓存决定 add/del，加入任务队列（不对缓存执行操作）
            boolean exists = ClientCableCache.hasLink(sel, clickedPos, clickedFace);
            CableOpPacket.Link link = new CableOpPacket.Link(
                sel.getX(), sel.getY(), sel.getZ(),
                clickedPos.getX(), clickedPos.getY(), clickedPos.getZ(),
                clickedFace.getName());
            CableClientQueue.enqueue(exists
                ? CableOpPacket.del(List.of(link), level.dimension(), player.getName().getString())
                : CableOpPacket.add(List.of(link), level.dimension(), player.getName().getString()));

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag)
    {
        TooltipLines.add(tooltip, "item.virtual_redstone_wire.virtual_cable.desc");
        TooltipLines.add(tooltip, "item.virtual_redstone_wire.virtual_cable.tip");
    }
}
