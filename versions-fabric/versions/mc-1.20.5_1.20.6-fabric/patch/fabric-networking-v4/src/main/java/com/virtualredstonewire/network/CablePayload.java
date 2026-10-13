package com.virtualredstonewire.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 本模组全部网络载荷的公共契约。
 *
 * 1.20.5 起原版接管了自定义载荷的收发（CustomPacketPayload + PayloadTypeRegistry），
 * Fabric 侧也随之从"按通道标识读写缓冲"改为"按载荷类型注册编解码器"：
 * 收发都以载荷对象本身为参数，编码与解码由注册该类型时给出的 StreamCodec 完成。
 * 这里让载荷接口直接继承原版 CustomPacketPayload，各载荷自己声明类型常量与编解码器，
 * 枢纽的投递方法因此仍能保持泛型形状，
 * 所有调用点（服务端队列、客户端队列、配置同步等）一行都不用改。
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
