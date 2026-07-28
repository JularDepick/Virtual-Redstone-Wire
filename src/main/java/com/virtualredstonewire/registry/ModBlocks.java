package com.virtualredstonewire.registry;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.block.VirtualRedstoneSourceBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks
{
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, VirtualRedstoneWire.MOD_ID);

    public static final RegistryObject<Block> VIRTUAL_SOURCE = BLOCKS.register("virtual_source",
        VirtualRedstoneSourceBlock::new);
}
