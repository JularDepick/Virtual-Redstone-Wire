package com.virtualredstonewire.item;

import com.virtualredstonewire.client.CableClientQueue;
import com.virtualredstonewire.client.ClientCableCache;
import com.virtualredstonewire.config.ClientConfig;
import com.virtualredstonewire.data.CableLink;
import com.virtualredstonewire.network.CableOpPacket;
import com.virtualredstonewire.network.CableProtocol;
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

import java.util.ArrayList;
import java.util.List;

public class CableCutterItem extends Item
{
    public CableCutterItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockPos pos = context.getClickedPos();

        if (level.isClientSide)
        {
            // v0.3.0：剪刀转换为删除该起点全部出链的单个 del 批量请求，加入任务队列
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
                return InteractionResult.PASS;
            }
            CableClientQueue.enqueue(
                CableOpPacket.del(batch, level.dimension(), player.getName().getString()));

            if (ClientConfig.enableChatFeedback.get())
            {
                player.sendSystemMessage(
                    Component.translatable("message.virtual_redstone_wire.cutter_removed",
                        pos.getX(), pos.getY(), pos.getZ()));
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.virtual_redstone_wire.cable_cutter.desc"));
    }
}
