package com.virtualredstonewire.mixin;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class MixinLevel
{
    private static final ThreadLocal<Boolean> COMPUTING = ThreadLocal.withInitial(() -> false);

    @Inject(method = "getSignal", at = @At("HEAD"), cancellable = true, remap = false)
    public void onGetSignal(BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir)
    {
        if (COMPUTING.get()) return;
        Level self = (Level) (Object) this;
        if (self.isClientSide()) return;

        COMPUTING.set(true);
        try
        {
            CableNetwork network = CableNetworkManager.get((ServerLevel) self);
            if (network.getLinkCount() == 0) return;
            int cable = network.getSignalAt(pos, direction, self);
            if (cable > 0)
            {
                cir.setReturnValue(cable);
            }
        }
        finally
        {
            COMPUTING.set(false);
        }
    }

    @Inject(method = "getDirectSignal", at = @At("HEAD"), cancellable = true, remap = false)
    public void onGetDirectSignal(BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir)
    {
        if (COMPUTING.get()) return;
        Level self = (Level) (Object) this;
        if (self.isClientSide()) return;

        COMPUTING.set(true);
        try
        {
            CableNetwork network = CableNetworkManager.get((ServerLevel) self);
            if (network.getLinkCount() == 0) return;
            int cable = network.getSignalAt(pos, direction, self);
            if (cable > 0)
            {
                cir.setReturnValue(cable);
            }
        }
        finally
        {
            COMPUTING.set(false);
        }
    }
}
