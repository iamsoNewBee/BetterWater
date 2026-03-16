package com.CatFish.BetterWater;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.FillBucketEvent;
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

    // 用于存储待处理的方块破坏事件（增加来源标记）
    private static final Queue<PendingBreakEvent> pendingEvents = new LinkedList<>();

    // ========== 流体兼容性支持 ==========
    private static Set<Block> fluidBlocks = null;

    // ========== 群系限制检测 ==========
    private static BiomeDictionary.Type[] validBiomeTypes = null;
    private static BiomeDictionary.Type[] bannedBiomeTypes = null;
    private static boolean regionCheckInitialized = false;

    /**
     * 初始化流体方块集合
     */
    private static void initFluidBlocks() {
        if (fluidBlocks != null) return;
        fluidBlocks = new HashSet<>();
        fluidBlocks.add(Blocks.water);
        fluidBlocks.add(Blocks.flowing_water);
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
            Block block = fluid.getBlock();
            if (block != null) {
                fluidBlocks.add(block);
            }
        }
        if (BetterWaterConfig.debugMode) {
            logger.info("Fluid blocks initialized: " + fluidBlocks.size() + " fluids registered.");
        }
    }

    public static boolean isFluidBlock(Block block) {
        if (fluidBlocks == null) {
            initFluidBlocks();
        }
        return fluidBlocks.contains(block);
    }

    // ========== 群系检测方法 ==========
    private static synchronized void initRegionCheck() {
        if (regionCheckInitialized) return;
        validBiomeTypes = new BiomeDictionary.Type[BetterWaterConfig.validBiomeDictionary.length];
        for (int i = 0; i < BetterWaterConfig.validBiomeDictionary.length; i++) {
            validBiomeTypes[i] = BiomeDictionary.Type.getType(BetterWaterConfig.validBiomeDictionary[i]);
        }
        bannedBiomeTypes = new BiomeDictionary.Type[BetterWaterConfig.bannedBiomeDictionary.length];
        for (int i = 0; i < BetterWaterConfig.bannedBiomeDictionary.length; i++) {
            bannedBiomeTypes[i] = BiomeDictionary.Type.getType(BetterWaterConfig.bannedBiomeDictionary[i]);
        }
        regionCheckInitialized = true;
    }

    private static boolean containsValidBiome(BiomeDictionary.Type[] currentTypes) {
        for (BiomeDictionary.Type current : currentTypes) {
            for (BiomeDictionary.Type valid : validBiomeTypes) {
                if (current == valid) return true;
            }
        }
        return false;
    }

    private static boolean containsBannedBiome(BiomeDictionary.Type[] currentTypes) {
        for (BiomeDictionary.Type current : currentTypes) {
            for (BiomeDictionary.Type banned : bannedBiomeTypes) {
                if (current == banned) return true;
            }
        }
        return false;
    }

    private static boolean isValidDim(int dim) {
        for (int d : BetterWaterConfig.validDims) {
            if (d == dim) return true;
        }
        return false;
    }

    private static boolean isBannedDim(int dim) {
        for (int d : BetterWaterConfig.bannedDims) {
            if (d == dim) return true;
        }
        return false;
    }

    private static boolean isOceanDim(int dim) {
        for (int d : BetterWaterConfig.oceanDims) {
            if (d == dim) return true;
        }
        return false;
    }

    public static boolean shouldGenerateSourceByRegion(World world, int x, int y, int z) {
        if (!regionCheckInitialized) {
            initRegionCheck();
        }
        int dim = world.provider.dimensionId;

        if (isOceanDim(dim)) {
            return true;
        }
        if (isBannedDim(dim)) {
            return false;
        }
        if (y < BetterWaterConfig.waterLower || y > BetterWaterConfig.waterUpper) {
            return false;
        }

        BiomeDictionary.Type[] biomes = BiomeDictionary.getTypesForBiome(world.getBiomeGenForCoords(x, z));
        if (containsBannedBiome(biomes)) {
            return false;
        }

        if (BetterWaterConfig.reverse) {
            return true;
        }

        return isValidDim(dim) && containsValidBiome(biomes);
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        BetterWaterConfig.init(event.getSuggestedConfigurationFile());
        FMLCommonHandler.instance().bus().register(this);
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
                processBlockBreak(pending); // 传入整个事件对象
            }
        }
    }

    @SubscribeEvent
    public void onFillBucket(FillBucketEvent event) {
        if (!BetterWaterConfig.enabled) return;

        MovingObjectPosition target = event.target;
        if (target == null || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;

        World world = event.world;
        int x = target.blockX;
        int y = target.blockY;
        int z = target.blockZ;

        Block block = world.getBlock(x, y, z);
        if (block == Blocks.water && world.getBlockMetadata(x, y, z) == 0) {
            synchronized (pendingEvents) {
                pendingEvents.add(new PendingBreakEvent(world, x, y, z, true)); // 标记为桶取水
            }
            if (BetterWaterConfig.debugMode) {
                logger.info(String.format("FillBucketEvent queued at [%d, %d, %d] (bucket)", x, y, z));
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
            pendingEvents.add(new PendingBreakEvent(world, x, y, z, false)); // 标记为方块破坏
        }
    }

    /**
     * 处理待办事件（区分来源）
     */
    private static void processBlockBreak(PendingBreakEvent event) {
        World world = event.world;
        int x = event.x;
        int y = event.y;
        int z = event.z;
        boolean fromBucket = event.fromBucket;

        Block currentBlock = world.getBlock(x, y, z);
        if (currentBlock != Blocks.air && currentBlock != Blocks.water && currentBlock != Blocks.flowing_water) {
            return;
        }

        // ===== 桶取水事件：优先使用群系检测 =====
        if (fromBucket) {
            if (shouldGenerateSourceByRegion(world, x, y, z)) {
                world.setBlock(x, y, z, Blocks.water, 0, 3);
                if (BetterWaterConfig.debugMode) {
                    logger.info(String.format("Placed water source at [%d, %d, %d] (bucket, region check)", x, y, z));
                }
                placeFlowingWater(world, x, y, z);
                return;
            }
            // 未通过群系检测，则继续执行下面的水域检测
        }

        // ===== 水域检测（适用于破坏事件，以及未通过群系检测的桶事件） =====
        // 只检查水平相邻的流体（确保破坏点与水体同一水平面）
        int[][] horizontalDirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };

        for (int[] dir : horizontalDirs) {
            int adjX = x + dir[0];
            int adjY = y + dir[1];
            int adjZ = z + dir[2];

            Block adjacentBlock = world.getBlock(adjX, adjY, adjZ);
            if (isFluidBlock(adjacentBlock)) {
                WaterDetectionResult result = detectConnectedWaterBody(world, adjX, adjY, adjZ);

                if (result.sourceCount >= BetterWaterConfig.waterBodyThreshold) {
                    world.setBlock(x, y, z, Blocks.water, 0, 3);
                    if (BetterWaterConfig.debugMode) {
                        logger.info(String.format("Placed water source at [%d, %d, %d]. Water body size: %d",
                            x, y, z, result.sourceCount));
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
                    logger.info(String.format("Placed flowing water (ID=1) at [%d, %d, %d] to trigger update",
                        adjX, adjY, adjZ));
                }
            }
        }
    }

    public static WaterDetectionResult detectConnectedWaterBody(World world, int startX, int startY, int startZ) {
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
            if (!isFluidBlock(block)) {
                continue;
            }

            totalWaterCount++;
            int metadata = world.getBlockMetadata(x, y, z);

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
                    || Math.abs(ny - startY) > BetterWaterConfig.maxVerticalRange) {
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

    /**
     * 待处理事件类（增加来源标记）
     */
    private static class PendingBreakEvent {
        final World world;
        final int x, y, z;
        final boolean fromBucket; // true=桶取水, false=方块破坏

        PendingBreakEvent(World world, int x, int y, int z, boolean fromBucket) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.fromBucket = fromBucket;
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
