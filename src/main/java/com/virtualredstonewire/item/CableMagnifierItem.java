package com.virtualredstonewire.item;

import com.virtualredstonewire.client.gui.CableInfoScreenOpener;
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
        if (player == null) return InteractionResult.PASS;

        if (level.isClientSide && player.isCrouching())
        {
            CableInfoScreenOpener.open(context.getClickedPos());
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.virtual_redstone_wire.cable_magnifier.desc"));
        tooltip.add(Component.translatable("item.virtual_redstone_wire.cable_magnifier.tip"));
    }
}
