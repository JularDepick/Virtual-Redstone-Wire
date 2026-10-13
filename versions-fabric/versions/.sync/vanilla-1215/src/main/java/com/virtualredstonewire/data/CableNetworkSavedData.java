package com.virtualredstonewire.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 线缆网络的存档载体(NBT)。
 * 而 NBT 字符串经 DataOutputStream.writeUTF 有 65535 字节硬上限(modified UTF-8),
 * - 写入: 关闭 JSON 美化(载荷约降为原来的三分之一以内), 若仍超过单字符串上限则按字符边界切块,
 */
public class CableNetworkSavedData extends SavedData
{
    private static final String DATA_NAME = VirtualRedstoneWire.MOD_ID + "_network";

    /**
     * 本层的存档类型声明: 构造器给出空网络, Codec 由本类的手写 NBT 读写经 xmap 包成,
     * 数据修复类型沿用原有的 LEVEL。
     */
    public static final SavedDataType<CableNetworkSavedData> TYPE = new SavedDataType<>(
        DATA_NAME,
        () -> new CableNetworkSavedData(new CableNetwork()),
        CompoundTag.CODEC.xmap(CableNetworkSavedData::fromTag, CableNetworkSavedData::toTag),
        DataFixTypes.LEVEL);

    /** 存档键: 新格式为字符串列表(分块), 旧格式为单个字符串 */
    private static final String KEY_CHUNKS = "network";
    private static final String KEY_CHUNK_COUNT = "networkChunks";
    private static final String KEY_TOTAL_CHARS = "networkChars";

    /**
     * 每块最大字节数。NBT 单字符串上限为 65535 字节(modified UTF-8), 这里留足余量:
     * 块长以字符计, 而 BMP 外字符会占 2 个 char, 故按字节上限的一半取值即绝对安全。
     */
    private static final int MAX_CHUNK_BYTES = 30000;

    /** 单次保存的载荷字符数上限, 超过视为异常数据, 拒绝写入并记录明确错误(避免写出超大 NBT) */
    private static final int MAX_PAYLOAD_CHARS = 8 * 1024 * 1024;

    private static final Gson GSON = new GsonBuilder().create();

    private CableNetwork network;

    public CableNetworkSavedData(CableNetwork network)
    {
        this.network = network;
    }

    public CableNetwork getNetwork()
    {
        return network;
    }

    public void setNetwork(CableNetwork network)
    {
        this.network = network;
    }

    private static CableNetworkSavedData fromTag(CompoundTag tag)
    {
        return load(tag);
    }

    private static CompoundTag toTag(CableNetworkSavedData data)
    {
        return data.save(new CompoundTag());
    }

    private CompoundTag save(CompoundTag tag)
    {
        String json = GSON.toJson(network.toJson());
        if (json.isEmpty())
        {
            /* 空网络也写出一个空列表, 保持"已保存"的语义, 读取侧据此判定为空网络*/
            tag.put(KEY_CHUNKS, new ListTag());
            tag.putInt(KEY_TOTAL_CHARS, 0);
            return tag;
        }
        if (json.length() > MAX_PAYLOAD_CHARS)
        {
            VirtualRedstoneWire.LOGGER.error(
                "Cable network payload too large to save ({} chars, limit {}), skipping this save "
                    + "to avoid corrupting the dimension save", json.length(), MAX_PAYLOAD_CHARS);
            return tag;
        }

        ListTag chunks = new ListTag();
        for (String chunk : chunk(json))
        {
            chunks.add(StringTag.valueOf(chunk));
        }
        tag.put(KEY_CHUNKS, chunks);
        tag.putInt(KEY_CHUNK_COUNT, chunks.size());
        tag.putInt(KEY_TOTAL_CHARS, json.length());
        return tag;
    }

    /** 按字符边界把载荷切成若干块; 生产写入与离机验证共用此实现, 保证两者不会漂移 */
    static List<String> chunk(String json)
    {
        List<String> result = new ArrayList<>();
        for (int offset = 0; offset < json.length(); offset += MAX_CHUNK_BYTES)
        {
            result.add(json.substring(offset, Math.min(offset + MAX_CHUNK_BYTES, json.length())));
        }
        return result;
    }

    private static CableNetworkSavedData load(CompoundTag tag)
    {
        /* 无任何本模组数据(全新世界): 属正常情况, 不记警告*/
        if (!tag.contains(KEY_CHUNKS))
        {
            return new CableNetworkSavedData(new CableNetwork());
        }

        String json = readPayload(tag);
        if (json == null || json.isEmpty())
        {
            /* 有键但内容为空/不可用: 可能是上一次保存失败留下的残缺数据, 记录警告后回退*/
            VirtualRedstoneWire.LOGGER.warn(
                "Cable network data is present but empty or unreadable, starting with an empty network");
            return new CableNetworkSavedData(new CableNetwork());
        }

        try
        {
            JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
            CableNetwork network = CableNetwork.fromJson(jsonObj);
            return new CableNetworkSavedData(network);
        }
        catch (Exception e)
        {
            /* 关键修复: 解析失败只回退为空网络, 不向上抛异常(原实现会把维度加载打断)*/
            VirtualRedstoneWire.LOGGER.error(
                "Failed to parse cable network data ({} chars), starting with an empty network", json.length(), e);
            return new CableNetworkSavedData(new CableNetwork());
        }
    }

    /** 读取载荷: 优先按新的字符串列表(分块)读取, 回退兼容旧的单个字符串形态 */
    private static String readPayload(CompoundTag tag)
    {
        Tag raw = tag.get(KEY_CHUNKS);
        if (raw == null) return null;

        if (raw.getId() == Tag.TAG_LIST)
        {
            ListTag chunks = (ListTag) raw;
            if (chunks.isEmpty()) return "";

            /* CompoundTag#getInt 改返回 Optional, 带默认值的取值改用 getIntOr*/
            int totalChars = tag.getIntOr(KEY_TOTAL_CHARS, -1);
            StringBuilder sb = new StringBuilder(totalChars > 0 ? totalChars : 1024);
            for (int i = 0; i < chunks.size(); i++)
            {
                Tag element = chunks.get(i);
                if (element.getId() != Tag.TAG_STRING)
                {
                    VirtualRedstoneWire.LOGGER.warn(
                        "Cable network chunk {} has unexpected tag type {}, treating data as unreadable",
                        i, element.getId());
                    return null;
                }
                /* Tag#getAsString 改名 asString 并返回 Optional*/
                sb.append(element.asString().orElse(""));
            }

            /* 写入时记录了精确的总字符数: 出现多余字符说明数据被污染, 以记录值为准截断*/
            if (totalChars >= 0)
            {
                if (sb.length() < totalChars)
                {
                    VirtualRedstoneWire.LOGGER.warn(
                        "Cable network data is truncated ({} of {} chars), treating data as unreadable",
                        sb.length(), totalChars);
                    return null;
                }
                if (sb.length() > totalChars)
                {
                    VirtualRedstoneWire.LOGGER.warn(
                        "Cable network data has {} trailing chars beyond the recorded length {}, truncating",
                        sb.length() - totalChars, totalChars);
                    sb.setLength(totalChars);
                }
            }
            return sb.toString();
        }

        if (raw.getId() == Tag.TAG_STRING)
        {
            /* 旧格式(单字符串): 兼容读取, 下次保存即迁移为列表形态*/
            String legacy = raw.asString().orElse(null);
            if (legacy == null)
            {
                VirtualRedstoneWire.LOGGER.warn(
                    "Cable network legacy single-string data is unreadable, starting with an empty network");
                return null;
            }
            VirtualRedstoneWire.LOGGER.info("Loading legacy single-string cable network data ({} chars)", legacy.length());
            return legacy;
        }

        VirtualRedstoneWire.LOGGER.warn(
            "Cable network data has unexpected tag type {}, starting with an empty network", raw.getId());
        return null;
    }

    public static CableNetworkSavedData get(ServerLevel level)
    {
        /* 取用入口收 SavedDataType, 数据修复类型与构造器都在该声明里给出*/
        return level.getDataStorage().computeIfAbsent(TYPE);
    }
}
