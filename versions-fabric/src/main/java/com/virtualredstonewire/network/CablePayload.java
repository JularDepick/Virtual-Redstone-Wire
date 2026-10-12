package com.virtualredstonewire.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 本模组全部网络载荷的公共契约。
 *
 * Forge 侧靠 SimpleChannel 统一登记编解码器并用 Object 泛型投递；
 * Fabric 侧按通道标识注册接收器、发送也必须逐类型进行。
 * 这里用一个只有"通道标识 + 写入缓冲"两件事的接口，
 * 使 CableNetworkChannel 的投递方法仍能保持泛型形状，
 * 从而让所有调用点（服务端队列、客户端队列、配置同步等）一行都不用改。
 */
public interface CablePayload
{
    /** 本载荷的通道标识 */
    ResourceLocation id();

    /** 把本载荷写入缓冲；与原有的 encode 语义一致 */
    void encode(FriendlyByteBuf buf);
}
