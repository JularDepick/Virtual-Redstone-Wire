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
 * 1.20.5 起 Forge 移除了 RenderGuiOverlayEvent, 原版 LayeredDraw 的实例又不公开,
 * 故 1.20.5+ 的适配目标改用本 Mixin 作为 HUD 挂载点。
 */
@Mixin(Gui.class)
public class MixinGui
{
    /**
     * remap = false 的依据: Forge 自 50.1 起运行时改用官方(Mojang)名, 不再需要 refmap;
     * 而开启重映射时注解处理器会去找 render 的混淆映射并报
     * "Unable to locate obfuscation mapping for @Inject target"(本项目未配置 refmap 链路,
     * 见 build.gradle 中"引入 @Inject/@Redirect 需先补 refmap"的说明)。
     * 这里直接给出官方名与方法描述符, 由 Mixin 按字面名匹配, 无需引入 refmap。
     */
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("TAIL"), remap = false)
    private void vred$renderActionBarHud(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci)
    {
        CableActionBarHud.renderHud(guiGraphics);
    }
}
