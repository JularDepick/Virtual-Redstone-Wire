package com.virtualredstonewire.registry;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModBlocks
{
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, VirtualRedstoneWire.MOD_ID);
}