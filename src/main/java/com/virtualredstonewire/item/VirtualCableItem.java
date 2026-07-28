package com.virtualredstonewire.item;

import com.virtualredstonewire.config.ClientConfig;
import com.virtualredstonewire.config.ServerConfig;
import com.virtualredstonewire.network.CableActionPacket;
import com.virtualredstonewire.network.CableNetworkChannel;
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

            double distance = Math.sqrt(sel.distSqr(clickedPos));
            int maxDist = ServerConfig.maxLinkDistance.get();
            if (distance > maxDist)
            {
                if (ClientConfig.enableChatFeedback.get())
                {
                    player.sendSystemMessage(
                        Component.translatable("message.virtual_redstone_wire.too_far", maxDist));
                }
                clearSelectedInput(player);
                return InteractionResult.PASS;
            }

            CableNetworkChannel.sendToServer(
                new CableActionPacket(true, sel, clickedPos, clickedFace));

            if (ClientConfig.enableChatFeedback.get())
            {
                player.sendSystemMessage(
                    Component.translatable("message.virtual_redstone_wire.link_created",
                        sel.getX(), sel.getY(), sel.getZ(),
                        clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()));
            }

            clearSelectedInput(player);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.virtual_redstone_wire.virtual_cable.desc"));
    }
}
