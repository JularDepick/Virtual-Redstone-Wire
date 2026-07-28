package com.virtualredstonewire.item;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import com.virtualredstonewire.network.CableNetworkChannel;
import com.virtualredstonewire.network.CableSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

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
        if (player == null) return InteractionResult.FAIL;

        BlockPos pos = context.getClickedPos();

        if (level.isClientSide) return InteractionResult.SUCCESS;

        CableNetwork network = CableNetworkManager.get(level);
        int removed = network.removeAllLinksAt(pos);

        if (removed > 0)
        {
            CableNetworkManager.markDirty(level);
            CableNetworkChannel.sendToAll((ServerLevel) level,
                new CableSyncPacket(network.getAllLinks()));
        }

        player.sendSystemMessage(
            Component.translatable("message.virtual-redstone-wire.cutter_removed", removed));

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.virtual-redstone-wire.cable_cutter.desc"));
    }
}
