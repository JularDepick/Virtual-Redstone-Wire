package com.virtualredstonewire.client;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.util.PressEdgeLatch;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 本模组三个交互物品(线缆/线缆剪/放大镜)的右键按下沿闸门。
 * 导致对空气右键也进入动作(已实测回归, 见 AGENTS.md 设计细节)。
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
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;
        /* 兜底复位: 松开后不再有 useOn 调用, 由逐 tick 检查把锁存位清掉*/
        if (!Minecraft.getInstance().options.keyUse.isDown()) LATCH.reset();
    }
}
