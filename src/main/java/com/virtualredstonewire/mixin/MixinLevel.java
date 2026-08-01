package com.virtualredstonewire.mixin;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * v0.4.0 纯 mixin 方案（零空间占用），v0.5.0 对齐 DBW 存储式语义：
 *   - 仅覆写 getSignal（不覆写 getDirectSignal，强充能/比较器/活塞行为回到原版）；
 *   - 与 DBW MixinServerLevel 一致：查询节点 (pos.relative(direction.getOpposite()), direction)
 *     的存储信号，返回 max(原版默认值, 网络存储信号)；
 *   - 节点 (out, dir) 语义：玩家点击输出方块 out 的 dir 面建链，红石灯放在 out
 *     本身上即可点亮（灯查询 getBestNeighborSignal(out) 时会调用
 *     getSignal(out.relative(dir), dir)，命中该节点）；
 *   - 信号为事件主动写入存储（ServerEvents），查询时纯读，无递归，无需环路保护。
 * 采用 interface-implementation 方式覆写 SignalGetter 的 default 方法 getSignal：
 * reobf 后方法名自动正确（混淆后为 m_277185_），产环境下 100% 生效。
 */
@Mixin(Level.class)
public abstract class MixinLevel implements SignalGetter
{
    @Override
    public int getSignal(BlockPos pos, Direction direction)
    {
        Level self = (Level) (Object) this;
        BlockState state = self.getBlockState(pos);

        // 原版 SignalGetter.getSignal 默认实现（作为 original 回退）
        int original = state.getSignal(self, pos, direction);
        if (state.shouldCheckWeakPower(self, pos, direction))
        {
            original = Math.max(original, maxDirectSignal(self, pos));
        }

        if (self.isClientSide()) return original;

        // DBW 语义：查询 pos 对面节点 (pos.relative(direction.getOpposite()), direction)
        BlockPos target = pos.relative(direction.getOpposite());
        CableNetwork network = CableNetworkManager.get((ServerLevel) self);
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
