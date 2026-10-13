package com.virtualredstonewire.client;

import com.virtualredstonewire.network.CableMsgPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 全量/追回响应分片缓冲（客户端）。
 * 收齐后一次性交给缓存与历史逻辑，避免单包超过 FriendlyByteBuf.writeUtf 的 32767 字符上限。
 */
public final class CableFullSyncBuffer
{
    private static List<CableMsgPacket.Change> changes = new ArrayList<>();
    private static int expectedChunks = 0;
    private static int receivedChunks = 0;
    private static String dimension = null;
    private static long version = 0;
    private static long since = 0;
    private static boolean expired = false;

    private CableFullSyncBuffer() {}

    /**
     * 接收一片；本批收齐返回合并后的变更列表，未收齐或本片被丢弃时返回 null。
     */
    public static List<CableMsgPacket.Change> accept(String dim, long v, boolean ex, long sc,
                                                     int chunkIndex, int chunkCount,
                                                     List<CableMsgPacket.Change> chunk)
    {
        boolean sameBatch = receivedChunks > 0
            && expectedChunks == chunkCount
            && (dim == null ? dimension == null : dim.equals(dimension))
            && v == version && sc == since && ex == expired;
        if (chunkIndex == 0 || !sameBatch)
        {
            begin(dim, v, sc, ex, chunkCount);
        }
        if (chunkIndex != receivedChunks)
        {
            /* 丢片或乱序：整批作废（TCP 有序下不可达，属防御分支）*/
            reset();
            return null;
        }
        if (chunk != null) changes.addAll(chunk);
        receivedChunks++;
        if (receivedChunks < expectedChunks) return null;
        List<CableMsgPacket.Change> merged = changes;
        reset();
        return merged;
    }

    private static void begin(String dim, long v, long sc, boolean ex, int chunkCount)
    {
        changes = new ArrayList<>();
        expectedChunks = chunkCount;
        receivedChunks = 0;
        dimension = dim;
        version = v;
        since = sc;
        expired = ex;
    }

    public static void reset()
    {
        changes = new ArrayList<>();
        expectedChunks = 0;
        receivedChunks = 0;
        dimension = null;
        version = 0;
        since = 0;
        expired = false;
    }
}
