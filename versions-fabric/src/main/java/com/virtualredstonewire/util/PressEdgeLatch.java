package com.virtualredstonewire.util;

/**
 * 按下沿锁存: 把"按住期间反复到达的回调"收敛为"每次按下只触发一次"。
 * 无状态外的依赖, 纯判定逻辑, 便于离机回归验证。
 */
public final class PressEdgeLatch
{
    /** 本段按住期间是否已放行过(初值为 false, 表示"尚未按住") */
    private boolean fired = false;

    /**
     * 按键状态推进一次, 返回本次是否应当触发。
     * keyDown 为 false 时复位并返回 false; 为 true 时仅在本段按住期首次调用返回 true。
     */
    public boolean shouldFire(boolean keyDown)
    {
        if (!keyDown)
        {
            fired = false;
            return false;
        }
        if (fired) return false;
        fired = true;
        return true;
    }

    /** 当前是否处于"本段按住已触发过"状态 */
    public boolean hasFired()
    {
        return fired;
    }

    /** 强制复位(登出/断线等状态清理点使用) */
    public void reset()
    {
        fired = false;
    }
}
