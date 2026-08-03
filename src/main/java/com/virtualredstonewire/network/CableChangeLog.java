package com.virtualredstonewire.network;

import com.virtualredstonewire.VirtualRedstoneWire;
import com.virtualredstonewire.config.ServerConfig;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * v0.3.0 链路服务端双日志（仅输出，不提供恢复功能）。
 * 操作日志（add/del，默认开启）：支持 TOTAL 合并 / PLAYER 按玩家分文件，
 * 行格式：[时间] @维度 $玩家$ 操作 {[from]->[to,面], ...} #结果。
 * 请求日志（调试级，默认关闭）：记录服务端收到的原始请求 JSON 与最终状态码，
 * 行格式：[时间] {原始请求 JSON} #状态码。
 */
public final class CableChangeLog
{
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxxx");

    private CableChangeLog() {}

    /** 操作日志（add/del 执行/拒绝）：total 仅合并文件 / player 仅按玩家 / both 两者都写 */
    public static void logOperation(ServerLevel level, String player, String op,
                                    java.util.List<CableOpPacket.Link> links, String result)
    {
        if (!ServerConfig.operationLogEnabled.get()) return;

        StringBuilder sb = new StringBuilder();
        sb.append('[').append(OffsetDateTime.now().format(TIME_FMT)).append("] @")
            .append(level.dimension().location()).append(" $").append(player).append("$ ")
            .append(op).append(" {");
        boolean first = true;
        for (CableOpPacket.Link l : links)
        {
            if (!first) sb.append(", ");
            sb.append('[').append(l.fx()).append(',').append(l.fy()).append(',').append(l.fz())
                .append("]->[").append(l.tx()).append(',').append(l.ty()).append(',').append(l.tz())
                .append(',').append(l.face()).append(']');
            first = false;
        }
        sb.append("} #").append(result);
        String line = sb.toString();

        ServerConfig.OperationLogSplit split = ServerConfig.operationLogSplit.get();
        if (split == ServerConfig.OperationLogSplit.TOTAL
            || split == ServerConfig.OperationLogSplit.BOTH)
        {
            writeLine(level, ServerConfig.operationLogFile.get(), line);
        }
        if (split == ServerConfig.OperationLogSplit.PLAYER
            || split == ServerConfig.OperationLogSplit.BOTH)
        {
            writeLine(level, operationFileName(player), line);
        }
    }

    /** 请求日志（调试级） */
    public static void logRequest(ServerLevel level, String rawJson, int code)
    {
        if (!ServerConfig.requestLogEnabled.get()) return;

        StringBuilder sb = new StringBuilder();
        sb.append('[').append(OffsetDateTime.now().format(TIME_FMT)).append("] {")
            .append(rawJson).append("} #").append(code);
        writeLine(level, ServerConfig.requestLogFile.get(), sb.toString());
    }

    /** 操作日志文件名：PLAYER 分割时主名后缀 _<玩家名>，保留扩展名 */
    private static String operationFileName(String player)
    {
        String file = ServerConfig.operationLogFile.get();
        if (ServerConfig.operationLogSplit.get() != ServerConfig.OperationLogSplit.PLAYER)
        {
            return file;
        }
        int dot = file.lastIndexOf('.');
        String base = dot > 0 ? file.substring(0, dot) : file;
        String ext = dot > 0 ? file.substring(dot) : "";
        return base + "_" + player + ext;
    }

    private static void writeLine(ServerLevel level, String fileName, String line)
    {
        try
        {
            Path path = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve(fileName);
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE))
            {
                w.write(line);
                w.write('\n');
            }
        }
        catch (IOException e)
        {
            VirtualRedstoneWire.LOGGER.warn("Failed to write cable server log: {}", e.getMessage());
        }
    }
}
