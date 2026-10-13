package com.virtualredstonewire.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 本模组全部网络载荷的公共契约。
 */
public interface CablePayload extends CustomPacketPayload
{
    /*
     * 收窄原版 type() 的返回类型：载荷类型常量本就以本模组载荷为元素，
     * 收窄后调用方无需再对 Type 的元素类型做转换。
     */
    @Override
    CustomPacketPayload.Type<? extends CablePayload> type();

    /** 把本载荷写入缓冲；与原有的 encode 语义一致，由各载荷的 StreamCodec 调用 */
    void encode(FriendlyByteBuf buf);
}
