package com.virtualredstonewire.mixin;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 纯 mixin 方案（零空间占用），对齐 DBW 存储式语义：
 * - 仅覆写 getSignal（不覆写 getDirectSignal，强充能/比较器/活塞行为回到原版）；
 */
@Mixin(Level.class)
public abstract class MixinLevel implements SignalGetter
{
    @Override
    public int getSignal(BlockPos pos, Direction direction)
    {
        Level self = (Level) (Object) this;
        BlockState state = self.getBlockState(pos);

        /* 原版 SignalGetter.getSignal 默认实现（作为 original 回退）*/
        int original = state.getSignal(self, pos, direction);
        if (state.shouldCheckWeakPower(self, pos, direction))
        {
            original = Math.max(original, maxDirectSignal(self, pos));
        }

        if (self.isClientSide()) return original;

        /* DBW 语义：查询 pos 对面节点 (pos.relative(direction.getOpposite()), direction)*/
        BlockPos target = pos.relative(direction.getOpposite());
        /* 直接传 Level（CableNetworkManager.get 接受 Level）：*/
        /* 强转 ServerLevel 在自定义 Level 子类上会 ClassCastException 且无收益*/
        CableNetwork network = CableNetworkManager.get(self);
        if (network != null)
        {
            int cable = network.getSignalAt(target, direction);
            if (cable > original) original = cable;
        }
        return original;
    }

    /**
     * 6 方向直接信号最大值（等价原版 SignalGetter.getDirectSignal(BlockPos)，
     * 但 MCP 环境接口未暴露该单参方法，故自行实现）。
     */
    private static int maxDirectSignal(Level self, BlockPos pos)
    {
        int max = 0;
        for (Direction d : Direction.values())
        {
            int v = self.getDirectSignal(pos.relative(d), d);
            if (v >= 15) return 15;
            if (v > max) max = v;
        }
        return max;
    }
}
