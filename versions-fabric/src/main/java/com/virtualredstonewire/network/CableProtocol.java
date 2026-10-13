package com.virtualredstonewire.network;

/**
 * 通信协议全局定义，升级至协议版本 4。
 */
public final class CableProtocol
{
    /* 协议版本（全局宏）*/
    public static final String PROTOCOL_VERSION = "4";

    /* 键名（通信缩写）*/
    public static final String KEY_TYPE = "tp";        /* type*/
    public static final String KEY_OP = "op";          /* operation*/
    public static final String KEY_VERSION = "v";      /* version*/
    public static final String KEY_DIMENSION = "dm";   /* dimension*/
    public static final String KEY_LINES = "ln";       /* lines*/
    public static final String KEY_FROM = "fr";        /* from*/
    public static final String KEY_TO = "to";          /* to*/
    public static final String KEY_FACE = "fc";        /* face*/
    public static final String KEY_PLAYER = "pl";      /* player*/
    public static final String KEY_SINCE = "sc";       /* since*/
    public static final String KEY_EXPIRED = "ex";     /* expired*/
    public static final String KEY_REQUEST = "rq";     /* request*/
    public static final String KEY_CODE = "cd";        /* code*/
    public static final String KEY_MESSAGE = "ms";     /* message*/
    public static final String KEY_LAST = "lx";        /* last external op（拒绝响应附带）*/
    public static final String KEY_CHUNK_INDEX = "ci"; /* chunk index（全量/追回响应分片序号，从 0 起）*/
    public static final String KEY_CHUNK_COUNT = "cc"; /* chunk count（全量/追回响应分片总数，单片为 1）*/

    /* 消息类型*/
    public static final String TYPE_DELTA = "d";       /* 增量广播*/
    public static final String TYPE_FULL = "f";        /* 全量/追回响应*/
    public static final String TYPE_REJECT = "r";      /* 拒绝响应*/

    /* 元操作*/
    public static final String OP_ADD = "add";
    public static final String OP_DEL = "del";
    public static final String OP_PULL = "pull";

    /* 错误码（数字，参考 HTTP 状态码风格）*/
    public static final int CODE_OK = 200;            /* 成功*/
    public static final int CODE_BAD_REQUEST = 400;    /* 格式/身份异常*/
    public static final int CODE_NOT_EXISTS = 404;     /* del 目标链路不存在*/
    public static final int CODE_CONFLICT = 409;       /* add 已存在 / 同 tick 相背*/
    public static final int CODE_UNPROCESSABLE = 422;  /* 语义校验失败*/
    public static final int CODE_TOO_MANY = 429;       /* 请求过于频繁（pull 限流）*/

    /* 批量上限*/
    public static final int BATCH_LIMIT = 256;

    /* 全量/追回响应分片上限（条/片）。单包载荷走 FriendlyByteBuf.writeUtf 的*/
    /* 32767 字符上限，单条链路 JSON 约 64-96 字符，故每片 200 条可稳定落在上限内*/
    public static final int FULL_RESPONSE_CHUNK_LINKS = 200;

    /* 服务端请求防护。同一玩家两次 pull 的最小间隔（毫秒）*/
    public static final long PULL_MIN_INTERVAL_MS = 1000L;

    /* 同一维度排队待处理请求的数量上限（超出即拒绝，防单客户端刷包）*/
    public static final int PENDING_QUEUE_LIMIT = 256;

    private CableProtocol() {}
}
