package com.virtualredstonewire.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.virtualredstonewire.VirtualRedstoneWire;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.util.datafix.DataFixTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * 线缆网络的存档载体(NBT)。
 *
 * v1.0.0 修复(原位补丁): 原实现把整个网络序列化成 prettyPrinting 的 JSON 后写入单个 NBT 字符串,
 * 而 NBT 字符串经 DataOutputStream.writeUTF 有 65535 字节硬上限(modified UTF-8),
 * 超过即抛 UTFDataFormatException; 该异常发生在 SavedData.save 返回之后(StringTag.write),
 * 无法在 save 内拦截, 结果是整个维度存档写不出去(存档静默失败)。
 * 且读取侧未防空: 磁盘上没有有效数据时 parseString(null) 抛 IllegalStateException,
 * 使"一次保存失败"升级为"每次进入世界都抛错并回退为空网络"。
 *
 * 1.20.5 适配: SavedData.save 增加 HolderLookup.Provider 形参,
 * Factory 的反序列化器由 Function 改为 BiFunction, 本类两处签名随之增补形参
 * (本载荷不访问注册表, 形参仅用于对齐原版签名)。
 *
 * 现实现:
 * - 写入: 关闭 JSON 美化(载荷约降为原来的三分之一以内), 若仍超过单字符串上限则按字符边界切块,
 *   存入 "network" 列表标签(每个元素各自受 65535 上限约束, 列表本身无上限), 并记录总字符数用于精确还原。
 * - 读取: 同时支持列表(新)与单个字符串(旧)两种形态; 数据缺失/为 null/类型不符/解析失败时
 *   只记录警告并回退为空网络, 绝不抛出异常中断维度加载。
 */
public class CableNetworkSavedData extends SavedData
{
    private static final String DATA_NAME = VirtualRedstoneWire.MOD_ID + "_network";

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

    // 1.20.5 起 save 增加 HolderLookup.Provider 形参（本载荷不访问注册表, 形参仅用于对齐原版签名）
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries)
    {
        String json = GSON.toJson(network.toJson());
        if (json.isEmpty())
        {
            // 空网络也写出一个空列表, 保持"已保存"的语义, 读取侧据此判定为空网络
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

    // 1.20.5 起 Factory 的反序列化器由 Function 改为 BiFunction, 静态 load 随之增补 Provider 形参
    public static CableNetworkSavedData load(CompoundTag tag, HolderLookup.Provider registries)
    {
        // 无任何本模组数据(全新世界): 属正常情况, 不记警告
        if (!tag.contains(KEY_CHUNKS))
        {
            return new CableNetworkSavedData(new CableNetwork());
        }

        String json = readPayload(tag);
        if (json == null || json.isEmpty())
        {
            // 有键但内容为空/不可用: 可能是上一次保存失败留下的残缺数据, 记录警告后回退
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
            // 关键修复: 解析失败只回退为空网络, 不向上抛异常(原实现会把维度加载打断)
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

            int totalChars = tag.contains(KEY_TOTAL_CHARS) ? tag.getInt(KEY_TOTAL_CHARS) : -1;
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
                sb.append(element.getAsString());
            }

            // 写入时记录了精确的总字符数: 出现多余字符说明数据被污染, 以记录值为准截断
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
            // 旧格式(单字符串): 兼容读取, 下次保存即迁移为列表形态
            String legacy = raw.getAsString();
            VirtualRedstoneWire.LOGGER.info("Loading legacy single-string cable network data ({} chars)", legacy.length());
            return legacy;
        }

        VirtualRedstoneWire.LOGGER.warn(
            "Cable network data has unexpected tag type {}, starting with an empty network", raw.getId());
        return null;
    }

    public static CableNetworkSavedData get(ServerLevel level)
    {
        // 1.20.2 起原版 DimensionDataStorage#computeIfAbsent 的三参数重载被移除, 改用 SavedData$Factory;
        // 1.20.5 起该 Factory 的反序列化器为 BiFunction, 方法引用随之指向双形参 load
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(() -> new CableNetworkSavedData(new CableNetwork()),
                CableNetworkSavedData::load, DataFixTypes.LEVEL),
            DATA_NAME);
    }
}
