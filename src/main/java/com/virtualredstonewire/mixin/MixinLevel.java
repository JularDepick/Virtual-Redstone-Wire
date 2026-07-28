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
    @Inject(method = "getSignal", at = @At("RETURN"), cancellable = true)
    public void onGetSignal(BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir)
    {
        if (((Level) (Object) this).isClientSide()) return;
        int cable = CableNetworkManager.get((ServerLevel) (Object) this).getSignalAt(pos, direction, (Level) (Object) this);
        if (cable > cir.getReturnValueI())
        {
            cir.setReturnValue(cable);
        }
    }

    @Inject(method = "getDirectSignal", at = @At("RETURN"), cancellable = true)
    public void onGetDirectSignal(BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir)
    {
        if (((Level) (Object) this).isClientSide()) return;
        int cable = CableNetworkManager.get((ServerLevel) (Object) this).getSignalAt(pos, direction, (Level) (Object) this);
        if (cable > cir.getReturnValueI())
        {
            cir.setReturnValue(cable);
        }
    }
}
