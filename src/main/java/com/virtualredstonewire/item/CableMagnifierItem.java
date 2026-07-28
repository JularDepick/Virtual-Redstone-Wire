package com.virtualredstonewire.item;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.network.CableNetworkChannel;
import com.virtualredstonewire.network.CableQueryPacket;
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
    public CableMagnifierItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;

        BlockPos pos = context.getClickedPos();

        if (level.isClientSide)
        {
            // 在客户端打开GUI
            net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.virtualredstonewire.client.gui.CableInfoScreen(pos));
        }
        else
        {
            // 服务端发送查询响应
            CableNetworkChannel.sendToPlayer((net.minecraft.server.level.ServerPlayer) player,
                new CableQueryPacket(pos));
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.virtual-redstone-wire.cable_magnifier.desc"));
        tooltip.add(Component.translatable("item.virtual-redstone-wire.cable_magnifier.tip"));
    }
}
