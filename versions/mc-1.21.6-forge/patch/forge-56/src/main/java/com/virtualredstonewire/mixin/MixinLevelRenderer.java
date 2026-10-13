package com.virtualredstonewire.mixin;

import com.virtualredstonewire.client.render.CableRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在渲染层绘制结束后挂载本模组的世界几何。
 * renderSectionLayer 已不存在, 改为注入 renderLevel 的尾部;
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
