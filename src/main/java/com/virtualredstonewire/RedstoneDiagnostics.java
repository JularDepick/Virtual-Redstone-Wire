package com.virtualredstonewire;

import com.virtualredstonewire.data.CableNetwork;
import com.virtualredstonewire.data.CableNetworkManager;
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
 */
public final class RedstoneDiagnostics
{
    public static final String TEST_PROPERTY = "vred.test";

    private RedstoneDiagnostics() {}

    public static void runAutoTestIfRequested(net.minecraft.server.MinecraftServer server)
    {
        if (!"true".equalsIgnoreCase(System.getProperty(TEST_PROPERTY, "false"))) return;
        VirtualRedstoneWire.LOGGER.info("[VREDTEST] system property detected, running redstone diagnostics");
        /* 只对主世界执行一次破坏性自动测试：遍历全部维度会在每个维度写入/清空方块并删除玩家已有链路*/
        ServerLevel overworld = server.overworld();
        if (overworld != null)
        {
            runTest(overworld);
        }
        VirtualRedstoneWire.LOGGER.info("[VREDTEST] diagnostics finished");
    }

    public static int runTest(ServerLevel level)
    {
        List<String> report = new ArrayList<>();
        int failures = 0;
        try
        {
            /* 选址：出生点上方空中，避开地形*/
            BlockPos spawn = level.getSharedSpawnPos();
            int x = spawn.getX();
            int z = spawn.getZ();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 2;

            BlockPos inputBlock = new BlockPos(x + 1, y, z);   /* 红石块（强充能源）*/
            BlockPos from = new BlockPos(x, y, z);             /* 输入端（石头，玩家点击的源方块）*/
            BlockPos lamp = new BlockPos(x, y, z + 3);         /* 输出端（红石灯本体，玩家点击的目标方块）*/
            Direction toFace = Direction.SOUTH;                /* 玩家点击目标方块的面 → 节点 (lamp, SOUTH)*/

            report.add(String.format("setup: inputBlock=%s from=%s lamp=%s toFace=SOUTH",
                inputBlock, from, lamp));

            level.setBlockAndUpdate(inputBlock, Blocks.REDSTONE_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(from, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());

            CableNetwork network = CableNetworkManager.get(level);
            /* 先清掉测试区域旧链路，保证可重复*/
            for (BlockPos pos : new BlockPos[]{from, lamp})
            {
                if (network.getNode(pos) != null)
                {
                    network.removeLinksFrom(pos, level);
                }
            }
            network.toggleLink(from, lamp, toFace, level);

            /* 写入信号：主动采集源方块真实信号（红石块相邻 → 15）*/
            network.refreshSource(level, from);

            int storedSignal = network.getSignalAt(lamp, toFace);                     /* 存储式信号 API*/
            int mixinHit = level.getSignal(lamp.relative(toFace), toFace);            /* mixin 路径：命中节点 (lamp,SOUTH)*/
            int mixinMiss = level.getSignal(lamp.relative(toFace.getOpposite()),
                toFace.getOpposite());                                                /* 反向查询：应命中 (lamp,NORTH)=0*/
            int inputSource = level.getBestNeighborSignal(from);                      /* 原版输入源*/
            boolean lampPowered = level.hasNeighborSignal(lamp);                      /* 红石灯被虚拟信号点亮？*/
            BlockState lampState = level.getBlockState(lamp);
            boolean lampLit = lampState.getBlock() instanceof RedstoneLampBlock
                && lampState.getValue(RedstoneLampBlock.LIT);

            report.add(String.format("network: links=%d hasNode(from)=%s hasNode(lamp)=%s",
                network.getLinkCount(), network.getNode(from) != null, network.getNode(lamp) != null));
            report.add(String.format("getSignalAt(lamp,SOUTH)=%d  (stored signal, expected 15)", storedSignal));
            report.add(String.format("level.getSignal(lamp.relative(SOUTH),SOUTH)=%d  (mixin hit, expected 15)", mixinHit));
            report.add(String.format("level.getSignal(lamp.relative(NORTH),NORTH)=%d  (mixin miss, expected 0)", mixinMiss));
            report.add(String.format("level.getBestNeighborSignal(from)=%d  (input source, expected 15)", inputSource));
            report.add(String.format("level.hasNeighborSignal(lamp)=%s  (expected true)", lampPowered));
            report.add(String.format("redstone lamp LIT=%s  (expected true)", lampLit));

            if (storedSignal != 15) failures++;
            if (mixinHit != 15) failures++;
            if (mixinMiss != 0) failures++;
            if (inputSource != 15) failures++;
            if (!lampPowered) failures++;
            if (!lampLit) failures++;

            report.add("RESULT: " + (failures == 0 ? "PASS" : "FAIL") + " (" + failures + " assertion(s) failed)");

            /* 场景 2：事件驱动（DBW 真实时序：先建链，后放源，不手动 refreshSource）*/
            /* 覆盖 ServerEventHandler.onNeighborBlockUpdate 的 updateSource 路径：*/
            /* 信号源邻居不得被排除，否则放红石块/拨拉杆后灯永不亮（实测教训）。*/
            network.removeLinksFrom(from, level);
            level.setBlockAndUpdate(inputBlock, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(from, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(lamp, Blocks.AIR.defaultBlockState());

            level.setBlockAndUpdate(from, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
            network.toggleLink(from, lamp, toFace, level); /* 建链瞬间 from 无信号，不调用 refreshSource*/
            level.setBlockAndUpdate(inputBlock, Blocks.REDSTONE_BLOCK.defaultBlockState()); /* 事件驱动写入*/

            int eventSignal = network.getSignalAt(lamp, toFace);
            boolean eventLampLit = level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT);
            report.add(String.format("event-driven: getSignalAt(lamp,SOUTH)=%d  (expected 15)", eventSignal));
            report.add(String.format("event-driven: lamp LIT=%s  (expected true)", eventLampLit));
            if (eventSignal != 15) failures++;
            if (!eventLampLit) failures++;
            report.add("RESULT2: " + (failures == 0 ? "PASS" : "FAIL") + " (" + failures + " assertion(s) failed total)");

            if (network.getNode(from) != null)
            {
                network.removeLinksFrom(from, level);
            }
            level.setBlockAndUpdate(inputBlock, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(from, Blocks.AIR.defaultBlockState());
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
