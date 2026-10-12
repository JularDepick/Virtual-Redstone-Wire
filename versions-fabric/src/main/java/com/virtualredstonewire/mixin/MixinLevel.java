package com.virtualredstonewire.mixin;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import com.virtualredstonewire.ServerEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

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
    /**
     * 邻居方块更新的挂载点。
     *
     * Forge 侧有 BlockEvent.NeighborNotifyEvent 直接给出"被通知的邻居集合"，
     * Fabric 侧没有对应事件，因此在原版邻居通知方法的尾部注入：
     * 通知集合即"除被跳过的那一面之外的全部方向"，与 Forge 事件的语义一致。
     * 挂尾部而非头部：原版是先改状态再通知邻居，尾部读到的是已生效的信号。
     */
    @Inject(method = "updateNeighborsAtExceptFromFacing", at = @At("TAIL"))
    private void virtualredstonewire$onNeighborNotify(BlockPos pos, Block block, Direction skipDirection,
                                                      CallbackInfo ci)
    {
        Level self = (Level) (Object) this;
        if (!(self instanceof ServerLevel serverLevel)) return;

        List<Direction> sides = new ArrayList<>(6);
        for (Direction direction : Direction.values())
        {
            if (direction != skipDirection) sides.add(direction);
        }
        ServerEventHandler.onNeighborUpdate(serverLevel, pos, sides);
    }

    @Override
    public int getSignal(BlockPos pos, Direction direction)
    {
        Level self = (Level) (Object) this;
        BlockState state = self.getBlockState(pos);

        // 原版 SignalGetter.getSignal 默认实现（作为 original 回退）
        int original = state.getSignal(self, pos, direction);
        // Forge 侧的 Level.getSignal 会在该方块需要检查弱充能时把直连信号一并计入最大值；
        // Fabric 侧没有这个加载器钩子，按其默认实现所用的原版判据 isRedstoneConductor 还原同一语义
        if (state.isRedstoneConductor(self, pos))
        {
            original = Math.max(original, maxDirectSignal(self, pos));
        }

        if (self.isClientSide()) return original;

        // DBW 语义：查询 pos 对面节点 (pos.relative(direction.getOpposite()), direction)
        BlockPos target = pos.relative(direction.getOpposite());
        // v1.0.0：直接传 Level（CableNetworkManager.get 接受 Level），
        // 原先强转 ServerLevel 在自定义 Level 子类上会 ClassCastException 且无任何收益
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
