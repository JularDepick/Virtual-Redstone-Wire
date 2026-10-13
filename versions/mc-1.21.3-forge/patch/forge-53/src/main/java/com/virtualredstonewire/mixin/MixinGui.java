package com.virtualredstonewire.mixin;

import com.virtualredstonewire.client.CableActionBarHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 GUI 渲染末尾绘制本模组的快捷栏上方飘浮提示。
 */
@Mixin(Gui.class)
public class MixinGui
{
    /**
     * remap = false 的依据: Forge 运行时改用官方(Mojang)名, 不再需要 refmap;
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"), remap = false)
    private void vred$renderActionBarHud(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci)
    {
        CableActionBarHud.renderHud(guiGraphics);
    }
}
