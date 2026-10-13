package com.virtualredstonewire.mixin;

import com.virtualredstonewire.client.render.CableRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在渲染层绘制结束后挂载本模组的世界几何。
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer
{
    @Inject(
        method = "renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
        at = @At("TAIL"),
        remap = false)
    private void vred$renderCableGeometry(RenderType renderType, double camX, double camY, double camZ,
                                          Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci)
    {
        CableRenderer.onRenderSectionLayer(renderType);
    }
}
