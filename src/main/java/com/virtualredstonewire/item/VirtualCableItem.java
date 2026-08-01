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
    private static final Map<UUID, BlockPos> SELECTED_INPUTS = new HashMap<>();

    public VirtualCableItem(Properties properties)
    {
        super(properties);
    }

    public static BlockPos getSelectedInput(Player player)
    {
        return SELECTED_INPUTS.get(player.getUUID());
    }

    public static void setSelectedInput(Player player, BlockPos pos)
    {
        SELECTED_INPUTS.put(player.getUUID(), pos.immutable());
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

    @Override
    public InteractionResult useOn(UseOnContext context)
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
                            clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()));
                }
                return InteractionResult.SUCCESS;
            }

            if (sel.equals(clickedPos))
            {
                clearSelectedInput(player);
                if (ClientConfig.enableChatFeedback.get())
                {
                    player.sendSystemMessage(
                        Component.translatable("message.virtual_redstone_wire.cancelled"));
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
                        Component.translatable("message.virtual_redstone_wire.too_far", maxDist));
                }
                return InteractionResult.PASS;
            }
            if (level.isEmptyBlock(sel))
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
