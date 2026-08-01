package com.virtualredstonewire.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
 * v0.3.0 链路信息变更日志（可选）。
 * 开启后每个操作请求记入世界目录下独立 JSON Lines 文件（完整字段名，无空白符）。
 * 仅输出，不提供恢复功能。
 */
public final class CableChangeLog
{
    private static final String FILE_NAME = "virtual_redstone_wire_network.log";
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxxx");

    private CableChangeLog() {}

    public static void log(ServerLevel level, long version, String player, String op,
                           java.util.List<CableOpPacket.Link> links, String result)
    {
        if (!ServerConfig.networkChangeLogEnabled.get()) return;

        JsonObject root = new JsonObject();
        root.addProperty("time", OffsetDateTime.now().format(TIME_FMT));
        root.addProperty("dimension", level.dimension().location().toString());
        root.addProperty("version", version);
        root.addProperty("player", player);
        root.addProperty("op", op);
        JsonArray arr = new JsonArray();
        for (CableOpPacket.Link l : links)
        {
            JsonObject o = new JsonObject();
            JsonArray fr = new JsonArray(); fr.add(l.fx()); fr.add(l.fy()); fr.add(l.fz());
            JsonArray to = new JsonArray(); to.add(l.tx()); to.add(l.ty()); to.add(l.tz());
            o.add("from", fr);
            o.add("to", to);
            o.addProperty("face", l.face());
            arr.add(o);
        }
        root.add("links", arr);
        root.addProperty("result", result);

        try
        {
            Path path = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve(FILE_NAME);
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE))
            {
                w.write(root.toString());
                w.write('\n');
            }
        }
        catch (IOException e)
        {
            VirtualRedstoneWire.LOGGER.warn("Failed to write cable network change log: {}", e.getMessage());
        }
    }
}
