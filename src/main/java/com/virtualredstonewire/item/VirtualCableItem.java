package com.virtualredstonewire.item;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.data.CableLink;
import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import com.virtualredstonewire.network.CableNetworkChannel;
import com.virtualredstonewire.network.CableUpdatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VirtualCableItem extends Item
{
    // 玩家输入端选中状态: Player UUID -> BlockPos
    private static final Map<UUID, BlockPos> selectedInputs = new HashMap<>();

    public VirtualCableItem(Properties properties)
    {
        super(properties);
    }

    public static BlockPos getSelectedInput(Player player)
    {
        return selectedInputs.get(player.getUUID());
    }

    public static void setSelectedInput(Player player, BlockPos pos)
    {
        selectedInputs.put(player.getUUID(), pos.immutable());
    }

    public static void clearSelectedInput(Player player)
    {
        selectedInputs.remove(player.getUUID());
    }

    public static boolean hasSelectedInput(Player player)
    {
        return selectedInputs.containsKey(player.getUUID());
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;

        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos selectedInput = getSelectedInput(player);

        if (selectedInput == null)
        {
            // 状态1: 未选中 — 选中输入端
            setSelectedInput(player, clickedPos);
            player.sendSystemMessage(
                Component.translatable("message.virtual-redstone-wire.selected_input",
                    formatPos(clickedPos)));
            return InteractionResult.SUCCESS;
        }

        if (selectedInput.equals(clickedPos))
        {
            // 状态2: 已选中，点击同一方块 — 取消选中
            clearSelectedInput(player);
            player.sendSystemMessage(
                Component.translatable("message.virtual-redstone-wire.cancelled"));
            return InteractionResult.SUCCESS;
        }

        // 状态3: 已选中，点击不同方块 — 创建/删除链路
        double distance = Math.sqrt(selectedInput.distSqr(clickedPos));
        if (distance > com.virtualredstonewire.config.ModConfig.maxLinkDistance.get())
        {
            player.sendSystemMessage(
                Component.translatable("message.virtual-redstone-wire.too_far"));
            return InteractionResult.FAIL;
        }

        CableNetwork network = CableNetworkManager.get(level);
        boolean created = network.toggleLink(selectedInput, clickedPos, clickedFace);

        CableNetworkManager.markDirty(level);

        CableUpdatePacket packet = new CableUpdatePacket(
            created, selectedInput, clickedPos, clickedFace);
        CableNetworkChannel.sendToAllTracking((ServerLevel) level, clickedPos, packet);

        if (created)
        {
            placeVirtualSource((ServerLevel) level, clickedPos, clickedFace);
            // 创建链路后立即标记脏，无需等待邻居更新事件
            com.virtualredstonewire.redstone.RedstoneCalculator.markDirty(clickedPos, clickedFace);
            player.sendSystemMessage(
                Component.translatable("message.virtual-redstone-wire.link_created",
                    formatPos(selectedInput), formatPos(clickedPos)));
        }
        else
        {
            removeVirtualSourceIfEmpty((ServerLevel) level, clickedPos, clickedFace);
            player.sendSystemMessage(
                Component.translatable("message.virtual-redstone-wire.link_removed",
                    formatPos(selectedInput), formatPos(clickedPos)));
        }

        clearSelectedInput(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                 List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.virtual-redstone-wire.virtual_cable.desc"));
    }

    private static String formatPos(BlockPos pos)
    {
        return "[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
    }

    private static void placeVirtualSource(ServerLevel level, BlockPos toPos, Direction toFace)
    {
        BlockPos sourcePos = toPos.relative(toFace.getOpposite());
        if (!level.getBlockState(sourcePos).isAir())
        {
            if (level.getBlockState(sourcePos).getBlock()
                instanceof com.virtualredstonewire.block.VirtualRedstoneSourceBlock)
            {
                return;
            }
        }
        level.setBlock(sourcePos,
            com.virtualredstonewire.registry.ModBlocks.VIRTUAL_SOURCE.get().defaultBlockState()
                .setValue(com.virtualredstonewire.block.VirtualRedstoneSourceBlock.FACING, toFace)
                .setValue(com.virtualredstonewire.block.VirtualRedstoneSourceBlock.POWER, 0),
            3);
    }

    private static void removeVirtualSourceIfEmpty(ServerLevel level, BlockPos toPos, Direction toFace)
    {
        BlockPos sourcePos = toPos.relative(toFace.getOpposite());
        CableNetwork network = CableNetworkManager.get(level);
        if (!network.hasOutputAt(toPos, toFace))
        {
            level.setBlock(sourcePos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
