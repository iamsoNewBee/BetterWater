package com.CatFish.BetterWater;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Logger;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.BlockEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

@Mod(modid = "betterwater", name = "Better Water", version = "2.0", acceptedMinecraftVersions = "[1.7.10]")
public class BetterWater {

    @Instance("betterwater")
    public static BetterWater instance;

    public static Logger logger = Logger.getLogger("betterwater");

    // 用于存储待处理的方块破坏事件
    private static final Queue<PendingBreakEvent> pendingEvents = new LinkedList<>();

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        BetterWaterConfig.init(event.getSuggestedConfigurationFile());
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        MinecraftForge.EVENT_BUS.register(this);
        logger.info("Better Water mod v2.0 loaded!");
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {}

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {}

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 处理所有待处理的方块破坏事件
        synchronized (pendingEvents) {
            while (!pendingEvents.isEmpty()) {
                PendingBreakEvent pending = pendingEvents.poll();
                processBlockBreak(pending.world, pending.x, pending.y, pending.z);
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!BetterWaterConfig.enabled) return;

        if (event.getPlayer() instanceof FakePlayer) return;

        World world = event.world;
        int x = event.x;
        int y = event.y;
        int z = event.z;

        int seaLevel = 63;
        if (y < seaLevel - BetterWaterConfig.seaLevelRange || y > seaLevel + BetterWaterConfig.seaLevelRange) {
            return;
        }

        Block brokenBlock = event.block;
        if (!BetterWaterConfig.isBlockAffected(brokenBlock)) return;

        // 将事件加入队列，按配置延迟处理
        synchronized (pendingEvents) {
            pendingEvents.add(new PendingBreakEvent(world, x, y, z));
        }
    }

    private void processBlockBreak(World world, int x, int y, int z) {
        // 检查破坏位置的方块是否已经被破坏
        Block currentBlock = world.getBlock(x, y, z);
        if (currentBlock != Blocks.air && currentBlock != Blocks.water && currentBlock != Blocks.flowing_water) {
            // 方块还在，可能事件被取消或者其他mod干预了
            return;
        }

        // 检查水平四个方向
        int[][] directions = { { 1, 0, 0 }, // 东
            { -1, 0, 0 }, // 西
            { 0, 0, 1 }, // 南
            { 0, 0, -1 } // 北
        };

        for (int[] dir : directions) {
            int adjX = x + dir[0];
            int adjY = y + dir[1];
            int adjZ = z + dir[2];

            Block adjacentBlock = world.getBlock(adjX, adjY, adjZ);

            // 检查是否是水方块（包括流动水）
            if (adjacentBlock == Blocks.water || adjacentBlock == Blocks.flowing_water) {
                // 检测连通水域中完整水源的数量
                WaterDetectionResult result = detectConnectedWaterBody(world, adjX, adjY, adjZ);

                if (result.sourceCount >= BetterWaterConfig.waterBodyThreshold) {
                    // 放置完整水源方块
                    world.setBlock(x, y, z, Blocks.water, 0, 3);
                    if (BetterWaterConfig.debugMode) {
                        logger.info(
                            String.format(
                                "Placed water source at [%d, %d, %d]. Water body size: %d",
                                x,
                                y,
                                z,
                                result.sourceCount));
                    }

                    // 在周围空气方块放置流动水以触发更新
                    placeFlowingWater(world, x, y, z);
                    return; // 找到符合条件的就返回，不继续检查其他方向
                }
            }
        }
    }

    /**
     * 在目标位置周围（水平方向及下方）的空气方块处放置流动水（ID=1）
     */
    private void placeFlowingWater(World world, int x, int y, int z) {
        // 检查方向：水平四个方向和正下方
        int[][] directions = { { 1, 0, 0 }, // 东
            { -1, 0, 0 }, // 西
            { 0, 0, 1 }, // 南
            { 0, 0, -1 }, // 北
            { 0, -1, 0 } // 下方
        };

        for (int[] dir : directions) {
            int adjX = x + dir[0];
            int adjY = y + dir[1];
            int adjZ = z + dir[2];

            Block adjacentBlock = world.getBlock(adjX, adjY, adjZ);
            // 如果是空气方块，放置流动水（metadata=1）
            if (adjacentBlock == Blocks.air) {
                world.setBlock(adjX, adjY, adjZ, Blocks.water, 1, 3);
                if (BetterWaterConfig.debugMode) {
                    logger.info(
                        String
                            .format("Placed flowing water (ID=1) at [%d, %d, %d] to trigger update", adjX, adjY, adjZ));
                }
            }
        }
    }

    /**
     * 检测连通水域并返回结果
     */
    private WaterDetectionResult detectConnectedWaterBody(World world, int startX, int startY, int startZ) {
        Queue<int[]> queue = new LinkedList<>();
        HashSet<String> visited = new HashSet<>();
        int sourceCount = 0;
        int totalWaterCount = 0;

        queue.add(new int[] { startX, startY, startZ });
        visited.add(key(startX, startY, startZ));

        while (!queue.isEmpty() && totalWaterCount < BetterWaterConfig.maxBlocksToCheck) {
            int[] pos = queue.poll();
            int x = pos[0];
            int y = pos[1];
            int z = pos[2];

            Block block = world.getBlock(x, y, z);

            // 如果不是水方块，跳过
            if (block != Blocks.water && block != Blocks.flowing_water) {
                continue;
            }

            totalWaterCount++;
            int metadata = world.getBlockMetadata(x, y, z);

            // 如果是完整水源方块，计数
            if (block == Blocks.water && metadata == 0) {
                sourceCount++;

                // 如果已经达到阈值，可以提前返回
                if (sourceCount >= BetterWaterConfig.waterBodyThreshold) {
                    return new WaterDetectionResult(sourceCount, totalWaterCount);
                }
            }

            // 向六个方向扩展搜索
            int[][] directions = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, -1, 0 } };

            for (int[] dir : directions) {
                int newX = x + dir[0];
                int newY = y + dir[1];
                int newZ = z + dir[2];

                // 检查是否在搜索范围内
                if (Math.abs(newX - startX) > BetterWaterConfig.searchRadius
                    || Math.abs(newZ - startZ) > BetterWaterConfig.searchRadius
                    || Math.abs(newY - startY) > 3) {
                    continue;
                }

                String key = key(newX, newY, newZ);
                if (!visited.contains(key)) {
                    visited.add(key);
                    queue.add(new int[] { newX, newY, newZ });
                }
            }
        }

        return new WaterDetectionResult(sourceCount, totalWaterCount);
    }

    private String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    /**
     * 存储待处理的方块破坏事件
     */
    private static class PendingBreakEvent {

        final World world;
        final int x, y, z;

        PendingBreakEvent(World world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /**
     * 存储水域检测结果
     */
    private static class WaterDetectionResult {

        final int sourceCount; // 完整水源数量
        final int totalWaterCount; // 总水方块数量

        WaterDetectionResult(int sourceCount, int totalWaterCount) {
            this.sourceCount = sourceCount;
            this.totalWaterCount = totalWaterCount;
        }
    }
}
