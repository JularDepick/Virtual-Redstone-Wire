package com.virtualredstonewire.network;

/**
 * v0.3.0 通信协议全局定义。
 * 协议版本为全局常量，升级协议格式时统一变更，版本不匹配的客户端无法通信。
 * 键名统一使用缩写（双端通信 minified JSON），缩写含义见
 * docs/v0.3.0-客户端与服务端通信机制与规范.md 第 3 章缩写表。
 */
public final class CableProtocol
{
    // 协议版本（全局宏）
    public static final String PROTOCOL_VERSION = "3";

    // 键名（通信缩写）
    public static final String KEY_TYPE = "tp";        // type
    public static final String KEY_OP = "op";          // operation
    public static final String KEY_VERSION = "v";      // version
    public static final String KEY_DIMENSION = "dm";   // dimension
    public static final String KEY_LINES = "ln";       // lines
    public static final String KEY_FROM = "fr";        // from
    public static final String KEY_TO = "to";          // to
    public static final String KEY_FACE = "fc";        // face
    public static final String KEY_PLAYER = "pl";      // player
    public static final String KEY_SINCE = "sc";       // since
    public static final String KEY_EXPIRED = "ex";     // expired
    public static final String KEY_REQUEST = "rq";     // request
    public static final String KEY_CODE = "cd";        // code
    public static final String KEY_MESSAGE = "ms";     // message

    // 消息类型
    public static final String TYPE_DELTA = "d";       // 增量广播
    public static final String TYPE_FULL = "f";        // 全量/追回响应
    public static final String TYPE_REJECT = "r";      // 拒绝响应

    // 元操作
    public static final String OP_ADD = "add";
    public static final String OP_DEL = "del";
    public static final String OP_PULL = "pull";

    // 错误码（数字，参考 HTTP 状态码风格）
    public static final int CODE_BAD_REQUEST = 400;    // 格式/身份异常
    public static final int CODE_NOT_EXISTS = 404;     // del 目标链路不存在
    public static final int CODE_CONFLICT = 409;       // add 已存在 / 同 tick 相背
    public static final int CODE_UNPROCESSABLE = 422;  // 语义校验失败

    // 批量上限
    public static final int BATCH_LIMIT = 256;

    private CableProtocol() {}
}
