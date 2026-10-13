package com.virtualredstonewire.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 本模组全部网络载荷的公共契约。
 * Fabric 侧按通道标识注册接收器、发送也必须逐类型进行。
 */
public interface CablePayload
{
    /** 本载荷的通道标识 */
    ResourceLocation id();

    /** 把本载荷写入缓冲；与原有的 encode 语义一致 */
    void encode(FriendlyByteBuf buf);
}
