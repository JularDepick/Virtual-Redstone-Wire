package com.virtualredstonewire.blockentity;

import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, VirtualRedstoneWire.MOD_ID);

    public static final RegistryObject<BlockEntityType<CableSignalBlockEntity>> CABLE_SIGNAL =
        BLOCK_ENTITIES.register("cable_signal",
            () -> BlockEntityType.Builder.of(CableSignalBlockEntity::new, net.minecraft.world.level.block.Blocks.AIR)
                .build(null));
}