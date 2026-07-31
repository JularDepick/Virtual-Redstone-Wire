package com.virtualredstonewire.mixin;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import org.spongepowered.asm.mixin.Mixin;

import java.util.HashSet;
import java.util.Set;

/**
 * v0.4.0 纯 mixin 方案（零空间占用）：
 * 采用 interface-implementation 方式覆写 SignalGetter 的 default 方法 getSignal/getDirectSignal。
 * 与旧版 @Inject(remap=false) 的区别：
 *   - reobf 后方法名自动正确（混淆后为 m_277185_ 等），不依赖 refmap/remap 参数，
 *     产环境（混淆 jar）下 100% 生效，根治"生产环境注入静默失效"问题；
 *   - 虚拟输出端（网络节点）返回网络信号，direction 无关（全向），
 *     玩家把红石灯放在输出端任意相邻面都能点亮，消除"点击面 vs 输出面"歧义；
 *   - 非虚拟节点回退原版逻辑（isSignalSource ? state.getSignal : 0），不影响其他红石。
 */
@Mixin(Level.class)
public abstract class MixinLevel implements SignalGetter
{
    /** 按位置环路保护：查询某 pos 过程中再次查询同一 pos 时直接返回 0，避免递归死循环。 */
    private static final ThreadLocal<Set<BlockPos>> COMPUTING =
        ThreadLocal.withInitial(HashSet::new);

    @Override
    public int getSignal(BlockPos pos, Direction direction)
    {
        Level self = (Level) (Object) this;
        if (self.isClientSide()) return 0;

        BlockPos key = pos.immutable();
        Set<BlockPos> computing = COMPUTING.get();
        if (computing.contains(key)) return 0;

        CableNetwork network = CableNetworkManager.get((ServerLevel) self);
        if (network != null && network.getLinkCount() > 0)
        {
            computing.add(key);
            try
            {
                int cable = network.getSignalAt(pos, direction, self);
                if (cable > 0) return cable;
            }
            finally
            {
                computing.remove(key);
            }
        }

        // 回退原版 SignalGetter.getSignal 默认实现
        var state = self.getBlockState(pos);
        return state.isSignalSource() ? state.getSignal(self, pos, direction) : 0;
    }

    @Override
    public int getDirectSignal(BlockPos pos, Direction direction)
    {
        Level self = (Level) (Object) this;
        if (self.isClientSide()) return 0;

        BlockPos key = pos.immutable();
        Set<BlockPos> computing = COMPUTING.get();
        if (computing.contains(key)) return 0;

        CableNetwork network = CableNetworkManager.get((ServerLevel) self);
        if (network != null && network.getLinkCount() > 0)
        {
            computing.add(key);
            try
            {
                int cable = network.getSignalAt(pos, direction, self);
                if (cable > 0) return cable;
            }
            finally
            {
                computing.remove(key);
            }
        }

        // 回退原版 SignalGetter.getDirectSignal 默认实现
        return self.getBlockState(pos).getDirectSignal(self, pos, direction);
    }
}
