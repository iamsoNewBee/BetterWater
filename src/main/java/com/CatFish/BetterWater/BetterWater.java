package com.CatFish.BetterWater;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

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

    // ========== 新增：流体兼容性支持 ==========
    /** 缓存所有流体方块（包括原版水和自定义流体） */
    private static Set<Block> fluidBlocks = null;

    /**
     * 初始化流体方块集合（惰性加载，在第一次检测时执行）
     */
    private static void initFluidBlocks() {
        if (fluidBlocks != null) return;
        fluidBlocks = new HashSet<>();
        // 添加原版水（确保包含，虽然它们也属于流体）
        fluidBlocks.add(Blocks.water);
        fluidBlocks.add(Blocks.flowing_water);
        // 遍历所有已注册的流体，添加其对应的方块
        for (Fluid fluid : FluidRegistry.getRegisteredFluids()
            .values()) {
            Block block = fluid.getBlock();
            if (block != null) {
                fluidBlocks.add(block);
            }
        }
        if (BetterWaterConfig.debugMode) {
            logger.info("Fluid blocks initialized: " + fluidBlocks.size() + " fluids registered.");
        }
    }

    /**
     * 判断一个方块是否为任意流体方块（包括流动和静止）
     */
    public static boolean isFluidBlock(Block block) {
        if (fluidBlocks == null) {
            initFluidBlocks();
        }
        return fluidBlocks.contains(block);
    }
    // ====================================

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

        synchronized (pendingEvents) {
            pendingEvents.add(new PendingBreakEvent(world, x, y, z));
        }
    }

    private static void processBlockBreak(World world, int x, int y, int z) {
        Block currentBlock = world.getBlock(x, y, z);
        if (currentBlock != Blocks.air && currentBlock != Blocks.water && currentBlock != Blocks.flowing_water) {
            return;
        }

        int[][] directions = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };

        for (int[] dir : directions) {
            int adjX = x + dir[0];
            int adjY = y + dir[1];
            int adjZ = z + dir[2];

            Block adjacentBlock = world.getBlock(adjX, adjY, adjZ);

            // 修改：使用 isFluidBlock 判断任意流体，而不只是原版水
            if (isFluidBlock(adjacentBlock)) {
                WaterDetectionResult result = detectConnectedWaterBody(world, adjX, adjY, adjZ);

                if (result.sourceCount >= BetterWaterConfig.waterBodyThreshold) {
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
                    placeFlowingWater(world, x, y, z);
                    return;
                }
            }
        }
    }

    private static void placeFlowingWater(World world, int x, int y, int z) {
        int[][] directions = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, -1, 0 } };

        for (int[] dir : directions) {
            int adjX = x + dir[0];
            int adjY = y + dir[1];
            int adjZ = z + dir[2];

            Block adjacentBlock = world.getBlock(adjX, adjY, adjZ);
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
     * 检测连通水域并返回结果（已修改为支持任意流体）
     */
    public static WaterDetectionResult detectConnectedWaterBody(World world, int startX, int startY, int startZ) {
        // 确保流体集合已初始化
        if (fluidBlocks == null) {
            initFluidBlocks();
        }

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

            // 使用 isFluidBlock 判断是否流体
            if (!isFluidBlock(block)) {
                continue;
            }

            totalWaterCount++;
            int metadata = world.getBlockMetadata(x, y, z);

            // 判断是否为静止源：metadata == 0 且是流体方块
            // 注意：如果需要排除岩浆等非水流体，可以在这里添加条件（例如通过 fluid 名称过滤）
            if (metadata == 0) {
                sourceCount++;
                if (sourceCount >= BetterWaterConfig.waterBodyThreshold) {
                    return new WaterDetectionResult(sourceCount, totalWaterCount);
                }
            }

            int[][] directions = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, -1, 0 } };

            for (int[] dir : directions) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];

                if (Math.abs(nx - startX) > BetterWaterConfig.searchRadius
                    || Math.abs(nz - startZ) > BetterWaterConfig.searchRadius
                    || Math.abs(ny - startY) > BetterWaterConfig.maxVerticalRange) { // 使用配置中的垂直范围
                    continue;
                }

                String key = key(nx, ny, nz);
                if (!visited.contains(key)) {
                    visited.add(key);
                    queue.add(new int[] { nx, ny, nz });
                }
            }
        }

        return new WaterDetectionResult(sourceCount, totalWaterCount);
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    /**
     * 从指定坐标开始检测连通水域（如果该坐标不是流体，则检查相邻六个方向）
     */
    public static WaterDetectionResult detectConnectedWaterBodyFromPos(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        if (isFluidBlock(block)) {
            return detectConnectedWaterBody(world, x, y, z);
        }
        int[][] dirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, -1, 0 } };
        for (int[] dir : dirs) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            int nz = z + dir[2];
            Block nb = world.getBlock(nx, ny, nz);
            if (isFluidBlock(nb)) {
                return detectConnectedWaterBody(world, nx, ny, nz);
            }
        }
        return new WaterDetectionResult(0, 0);
    }

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

    public static class WaterDetectionResult {

        public final int sourceCount;
        public final int totalWaterCount;

        public WaterDetectionResult(int sourceCount, int totalWaterCount) {
            this.sourceCount = sourceCount;
            this.totalWaterCount = totalWaterCount;
        }
    }
}
