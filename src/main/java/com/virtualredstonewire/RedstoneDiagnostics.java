package com.virtualredstonewire;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
import com.virtualredstonewire.redstone.RedstoneCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * 红石信号自动化诊断器（仅调试用）：
 * 在服务端搭建可复现的测试装置——
 *   [红石块] FROM(石头) ===虚拟链路===> TO(石头) [红石灯]
 * 然后逐项读取信号并输出诊断日志，据此判断：
 *   1) 网络链路是否建好（节点/入边）；
 *   2) 输入源读取是否正确（getBestNeighborSignal(FROM)=15）；
 *   3) mixin 覆写是否生效（level.getSignal(TO, 任意方向) 是否走网络返回 15）；
 *   4) 全向输出是否成立（NORTH/SOUTH/UP 三个方向查询均应为 15）；
 *   5) 红石灯是否被虚拟信号点亮（hasNeighborSignal(lamp)=true、LIT=true）。
 * 触发方式：玩家执行 /vredtest，或在 JVM 加 -Dvred.test=true 启动服务器自动运行。
 */
public final class RedstoneDiagnostics
{
    public static final String TEST_PROPERTY = "vred.test";

    private RedstoneDiagnostics() {}

    public static void runAutoTestIfRequested(net.minecraft.server.MinecraftServer server)
    {
        if (!"true".equalsIgnoreCase(System.getProperty(TEST_PROPERTY, "false"))) return;
        VirtualRedstoneWire.LOGGER.info("[VREDTEST] system property detected, running redstone diagnostics");
        for (ServerLevel level : server.getAllLevels())
        {
            runTest(level);
        }
        VirtualRedstoneWire.LOGGER.info("[VREDTEST] diagnostics finished");
    }

    public static int runTest(ServerLevel level)
    {
        List<String> report = new ArrayList<>();
        int failures = 0;
        try
        {
            // ---------- 选址：出生点上方空中，避开地形 ----------
            BlockPos spawn = level.getSharedSpawnPos();
            int x = spawn.getX();
            int z = spawn.getZ();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 2;

            BlockPos inputBlock = new BlockPos(x + 1, y, z);   // 红石块（强充能源）
            BlockPos from = new BlockPos(x, y, z);             // 输入端（石头）
            BlockPos to = new BlockPos(x, y, z + 3);           // 输出端（石头）
            BlockPos lamp = new BlockPos(x, y, z + 4);         // 红石灯（被测输出）
            Direction toFace = Direction.SOUTH;                // 链路记录的输出面（信号查询时忽略方向）

            report.add(String.format("setup: inputBlock=%s from=%s to=%s lamp=%s toFace=SOUTH",
                inputBlock, from, to, lamp));

            // ---------- 搭建 ----------
            level.setBlockAndUpdate(inputBlock, Blocks.REDSTONE_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(from, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(to, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());

            CableNetwork network = CableNetworkManager.get(level);
            // 先清掉测试区域旧链路，保证可重复
            for (BlockPos pos : new BlockPos[]{from, to})
            {
                if (network.getNode(pos) != null)
                {
                    network.removeLinksFrom(pos, level);
                }
            }
            network.toggleLink(from, to, toFace, level);

            // ---------- 触发更新传播 ----------
            RedstoneCalculator.markDirtyInput(level, from);
            RedstoneCalculator.propagateUpdates(level, from);
            level.updateNeighborsAt(to, level.getBlockState(to).getBlock());
            level.updateNeighborsAt(lamp, level.getBlockState(lamp).getBlock());

            // ---------- 诊断项 ----------
            int networkSignal = network.getSignalAt(to, null, level);          // 网络 API 总信号
            int sigNorth = level.getSignal(to, Direction.NORTH);               // mixin 覆写路径（全向1）
            int sigSouth = level.getSignal(to, Direction.SOUTH);               // mixin 覆写路径（全向2）
            int sigUp = level.getSignal(to, Direction.UP);                     // mixin 覆写路径（全向3）
            int inputSource = level.getBestNeighborSignal(from);               // 原版输入源
            boolean lampPowered = level.hasNeighborSignal(lamp);               // 红石灯被虚拟信号点亮？
            BlockState lampState = level.getBlockState(lamp);
            boolean lampLit = lampState.getBlock() instanceof RedstoneLampBlock
                && lampState.getValue(RedstoneLampBlock.LIT);

            report.add(String.format("network: links=%d hasNode(from)=%s hasNode(to)=%s",
                network.getLinkCount(), network.getNode(from) != null, network.getNode(to) != null));
            report.add(String.format("getSignalAt(to,null)=%d  (network API, expected 15)", networkSignal));
            report.add(String.format("level.getSignal(to,NORTH)=%d  (mixin getSignal, expected 15)", sigNorth));
            report.add(String.format("level.getSignal(to,SOUTH)=%d  (mixin getSignal, expected 15)", sigSouth));
            report.add(String.format("level.getSignal(to,UP)=%d  (mixin getSignal, expected 15)", sigUp));
            report.add(String.format("level.getBestNeighborSignal(from)=%d  (input source, expected 15)", inputSource));
            report.add(String.format("level.hasNeighborSignal(lamp)=%s  (expected true)", lampPowered));
            report.add(String.format("redstone lamp LIT=%s  (expected true)", lampLit));

            // ---------- 判定 ----------
            if (networkSignal != 15) failures++;
            if (sigNorth != 15) failures++;
            if (sigSouth != 15) failures++;
            if (sigUp != 15) failures++;
            if (inputSource != 15) failures++;
            if (!lampPowered) failures++;
            if (!lampLit) failures++;

            report.add("RESULT: " + (failures == 0 ? "PASS" : "FAIL") + " (" + failures + " assertion(s) failed)");

            // ---------- 清理 ----------
            if (network.getNode(from) != null)
            {
                network.removeLinksFrom(from, level);
            }
            level.setBlockAndUpdate(inputBlock, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(from, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(to, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(lamp, Blocks.AIR.defaultBlockState());
            CableNetworkManager.markDirty(level);
        }
        catch (Exception e)
        {
            failures = -1;
            report.add("EXCEPTION: " + e);
            VirtualRedstoneWire.LOGGER.error("[VREDTEST] exception during diagnostics", e);
        }

        for (String line : report)
        {
            VirtualRedstoneWire.LOGGER.info("[VREDTEST] {}", line);
        }
        return failures;
    }
}
