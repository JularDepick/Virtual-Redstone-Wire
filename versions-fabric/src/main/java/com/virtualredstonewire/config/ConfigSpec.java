package com.virtualredstonewire.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/*
 * Fabric 侧没有配置 API，这里自建一套最小配置设施：
 * 每种值都是一个带 get/set 的条目对象，由 ConfigSpec 统一负责分段、读取与写回。
 *
 * 条目对象刻意保持与 Forge 侧 ForgeConfigSpec 的取值对象同形（都是 get/set），
 * 这样平台无关的调用方（logs、command、data 等）无需任何改动即可编译。
 *
 * 文件格式与 Forge 侧一致：分段标题 + key = value，读取时忽略分段与注释，
 * 因此两种加载器写出的配置文件可以互相读懂。
 */
public abstract class ConfigSpec
{
    private final List<Entry<?>> entries = new ArrayList<>();
    private final Map<String, Entry<?>> byKey = new LinkedHashMap<>();
    private Path file;

    /** 配置文件名，位于 Fabric 的配置目录下 */
    public abstract String fileName();

    protected <T extends Entry<?>> T add(T entry)
    {
        entries.add(entry);
        byKey.put(entry.key, entry);
        return entry;
    }

    public List<Entry<?>> entries()
    {
        return entries;
    }

    public Entry<?> entry(String key)
    {
        return byKey.get(key);
    }

    /** 从配置目录读取；文件不存在时保留默认值，不报错 */
    public void load(Path dir)
    {
        file = dir.resolve(fileName());
        if (!Files.isRegularFile(file)) return;
        List<String> lines;
        try
        {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            return;
        }
        for (String line : lines)
        {
            String text = line.trim();
            if (text.isEmpty() || text.startsWith("#") || text.startsWith("[")) continue;
            int split = text.indexOf('=');
            if (split <= 0) continue;
            String key = text.substring(0, split).trim();
            String raw = text.substring(split + 1).trim();
            raw = stripQuotes(raw);
            for (Entry<?> entry : entries)
            {
                if (entry.key.equals(key))
                {
                    entry.parse(raw);
                    break;
                }
            }
        }
    }

    /** 写回配置文件；条目按登记顺序输出，并保留分段标题 */
    public void save()
    {
        if (file == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("# Virtual Redstone Wire 配置文件\n");
        sb.append("# 与 Forge 侧同名同义，可互相读懂\n\n");
        String section = null;
        for (Entry<?> entry : entries)
        {
            if (!entry.section.equals(section))
            {
                section = entry.section;
                sb.append('[').append(section).append("]\n");
            }
            sb.append(entry.key).append(" = ").append(entry.serialize()).append('\n');
        }
        try
        {
            Files.createDirectories(file.getParent());
            Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            // 配置写盘失败不影响运行期的内存值
        }
    }

    private static String stripQuotes(String raw)
    {
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\""))
        {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    /** 配置条目基类：键、所属分段、默认值与当前值 */
    public abstract static class Entry<T>
    {
        final String section;
        final String key;
        final T defaultValue;
        T value;

        Entry(String section, String key, T defaultValue)
        {
            this.section = section;
            this.key = key;
            this.defaultValue = defaultValue;
            this.value = defaultValue;
        }

        public String key()
        {
            return key;
        }

        public String section()
        {
            return section;
        }

        public T get()
        {
            return value;
        }

        public T getDefault()
        {
            return defaultValue;
        }

        public void set(T newValue)
        {
            if (newValue != null) value = newValue;
        }

        /** 恢复默认值 */
        public void reset()
        {
            value = defaultValue;
        }

        abstract void parse(String raw);

        abstract String serialize();
    }

    /** 整数值条目：越界值在写入与读取时都被丢弃 */
    public static final class IntEntry extends Entry<Integer>
    {
        private final int min;
        private final int max;

        public IntEntry(String section, String key, int defaultValue, int min, int max)
        {
            super(section, key, defaultValue);
            this.min = min;
            this.max = max;
        }

        public int min()
        {
            return min;
        }

        public int max()
        {
            return max;
        }

        @Override
        public void set(Integer newValue)
        {
            if (newValue == null) return;
            if (newValue < min || newValue > max) return;
            value = newValue;
        }

        @Override
        void parse(String raw)
        {
            try
            {
                set(Integer.parseInt(raw.trim()));
            }
            catch (NumberFormatException ignored)
            {
                // 非法值保留原值
            }
        }

        @Override
        String serialize()
        {
            return Integer.toString(value);
        }
    }

    /** 布尔值条目 */
    public static final class BoolEntry extends Entry<Boolean>
    {
        public BoolEntry(String section, String key, boolean defaultValue)
        {
            super(section, key, defaultValue);
        }

        @Override
        void parse(String raw)
        {
            value = Boolean.parseBoolean(raw.trim());
        }

        @Override
        String serialize()
        {
            return Boolean.toString(value);
        }
    }

    /** 字符串条目：序列化时加引号，与 TOML 习惯一致 */
    public static final class StringEntry extends Entry<String>
    {
        public StringEntry(String section, String key, String defaultValue)
        {
            super(section, key, defaultValue);
        }

        @Override
        void parse(String raw)
        {
            value = raw;
        }

        @Override
        String serialize()
        {
            return '"' + value + '"';
        }
    }

    /** 枚举条目：以大写名序列化，与 Forge 侧一致 */
    public static final class EnumEntry<E extends Enum<E>> extends Entry<E>
    {
        private final Class<E> type;

        public EnumEntry(String section, String key, E defaultValue)
        {
            super(section, key, defaultValue);
            this.type = defaultValue.getDeclaringClass();
        }

        public E[] values()
        {
            return type.getEnumConstants();
        }

        @Override
        void parse(String raw)
        {
            try
            {
                value = Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ignored)
            {
                // 非法枚举名保留原值
            }
        }

        @Override
        String serialize()
        {
            return value.name().toLowerCase(Locale.ROOT);
        }
    }
}
