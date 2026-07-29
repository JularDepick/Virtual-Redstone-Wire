package com.virtualredstonewire.blockentity;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CableSignalBlockEntity extends BlockEntity
{
    public CableSignalBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.CABLE_SIGNAL.get(), pos, state);
    }

    public static void updateSignal(net.minecraft.world.level.Level level, BlockPos pos)
    {
        if (!level.isClientSide())
        {
            level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        }
    }
}