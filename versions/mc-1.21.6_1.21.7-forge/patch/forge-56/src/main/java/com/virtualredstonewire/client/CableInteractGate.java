package com.virtualredstonewire.client;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.util.PressEdgeLatch;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 本模组三个交互物品(线缆/线缆剪/放大镜)的右键按下沿闸门。
 *
 * 触发时机: 右键按下即触发, 松开前不再触发(长按不重复)。
 * 背景: 原版 Minecraft.handleKeybinds 在右键长按期间每 4 tick 重新调用 startUseItem,
 * 三个物品未覆写 getUseDuration(isUsingItem() 恒为 false), 重复不被抑制,
 * 于是长按会让"选中/取消选中"、"建链/删链"、面板反复重开重复发生。
 *
 * 实现口径(重要): 本类**不拦截也不取消**任何原版事件, 只提供"本次按下是否应当触发"的判定,
 * 由三个物品在自己的 useOn 内调用 shouldFireOnce() 决定是否执行动作。
 * 之所以不用 InputEvent.InteractionKeyMappingTriggered 自行执行动作并 setCanceled,
 * 是因为那会绕过原版 startUseItem 的判定链(命中方块类型判定、主手优先、onItemUseFirst、
 * 方块自身 use 等), 其中"未命中方块时不进入 ItemStack.useOn"一步会被绕过,
 * 导致对空气右键也进入动作(已实测回归, 见 AGENTS.md 设计细节)。
 *
 * 锁存位在右键松开时复位: 事件回调内判断 + 逐 tick 兜底。
 */
@Mod.EventBusSubscriber(modid = VirtualRedstoneWire.MOD_ID, value = Dist.CLIENT)
public class CableInteractGate
{
    /** 本次右键按下是否已经触发过动作(松开时复位) */
    private static final PressEdgeLatch LATCH = new PressEdgeLatch();

    /**
     * 本次右键按下是否应当触发动作。仅由三个物品的 useOn 调用。
     * keyUse 未按住时复位并返回 false; 一段按住期间只放行第一次。
     */
    public static boolean shouldFireOnce()
    {
        return LATCH.shouldFire(Minecraft.getInstance().options.keyUse.isDown());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event)
    {
        // 兜底复位: 松开后不再有 useOn 调用, 由逐 tick 检查把锁存位清掉
        if (!Minecraft.getInstance().options.keyUse.isDown()) LATCH.reset();
    }
}
