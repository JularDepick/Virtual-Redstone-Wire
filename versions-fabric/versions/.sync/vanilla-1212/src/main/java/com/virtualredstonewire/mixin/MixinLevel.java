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
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯 mixin 方案（零空间占用），对齐 DBW 存储式语义：
 * - 仅覆写 getSignal（不覆写 getDirectSignal，强充能/比较器/活塞行为回到原版）；
 */
@Mixin(Level.class)
public abstract class MixinLevel implements SignalGetter
{
    /**
     * 邻居方块更新的挂载点。
     * 适配: 原版给该方法新增了第四个形参 Orientation(红石更新来源与传播方向),
     * 且是"三参重载被移除"而非并存, 因此处理器形参表必须同步增补该形参。
     */
    @Inject(method = "updateNeighborsAtExceptFromFacing", at = @At("TAIL"))
    private void virtualredstonewire$onNeighborNotify(BlockPos pos, Block block, Direction skipDirection,
                                                      Orientation orientation, CallbackInfo ci)
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

        /* 原版 SignalGetter.getSignal 默认实现（作为 original 回退）*/
        int original = state.getSignal(self, pos, direction);
        /* Forge 侧的 Level.getSignal 会在该方块需要检查弱充能时把直连信号一并计入最大值；*/
        /* Fabric 侧没有这个加载器钩子，按其默认实现所用的原版判据 isRedstoneConductor 还原同一语义*/
        if (state.isRedstoneConductor(self, pos))
        {
            original = Math.max(original, maxDirectSignal(self, pos));
        }

        if (self.isClientSide()) return original;

        /* DBW 语义：查询 pos 对面节点 (pos.relative(direction.getOpposite()), direction)*/
        BlockPos target = pos.relative(direction.getOpposite());
        /* 直接传 Level（CableNetworkManager.get 接受 Level），*/
        /* 原先强转 ServerLevel 在自定义 Level 子类上会 ClassCastException 且无任何收益*/
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
