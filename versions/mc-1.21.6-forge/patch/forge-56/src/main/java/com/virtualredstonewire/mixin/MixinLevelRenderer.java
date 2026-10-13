package com.virtualredstonewire.mixin;

import com.virtualredstonewire.client.render.CableRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在渲染层绘制结束后挂载本模组的世界几何。
 *
 * 1.21.3 起该版本线没有可用的世界渲染事件(RenderLevelStageEvent 只存在于 51.x/52.x,
 * AddFramePassEvent 自 54.x 起才有), 故改用注入原版 LevelRenderer 的方式。
 * 1.21.6 起 renderSectionLayer 已不存在, 改为注入 renderLevel 的尾部;
 * 该位置在全部渲染层之后, 且不再需要按 RenderType 筛选。
 *
 * remap = false 的依据同 MixinGui: Forge 50.1 起运行时改用官方名, 本项目未配置 refmap 链路,
 * 因此直接给出官方名与方法描述符, 由 Mixin 按字面名匹配。
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer
{
    @Inject(
        method = "renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
        at = @At("TAIL"),
        remap = false)
    private void vred$renderCableGeometry(CallbackInfo ci)
    {
        CableRenderer.onRenderLevelEnd();
    }
}
