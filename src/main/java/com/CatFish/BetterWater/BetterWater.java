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

    // ========== 水区域检测所需变量 ==========
    private static BiomeDictionary.Type[] validBiomeTypes = null;
    private static BiomeDictionary.Type[] bannedBiomeTypes = null;
    private static boolean regionCheckInitialized = false;

    public static Logger logger = Logger.getLogger("betterwater");

    private static final Queue<PendingBreakEvent> pendingEvents = new LinkedList<>();

    private static Set<Block> fluidBlocks = null;

    // 群系缓存（略，原样）

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
        for (int d : BetterWaterConfig.validDims) if (d == dim) return true;
        return false;
    }

    private static boolean isBannedDim(int dim) {
        for (int d : BetterWaterConfig.bannedDims) if (d == dim) return true;
        return false;
    }

    private static boolean isOceanDim(int dim) {
        for (int d : BetterWaterConfig.oceanDims) if (d == dim) return true;
        return false;
    }

    private static void initFluidBlocks() {
        if (fluidBlocks != null) return;
        fluidBlocks = new HashSet<>();
        fluidBlocks.add(Blocks.water);
        fluidBlocks.add(Blocks.flowing_water);
        fluidBlocks.add(Blocks.lava);
        fluidBlocks.add(Blocks.flowing_lava);
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
            Block block = fluid.getBlock();
            if (block != null) fluidBlocks.add(block);
        }
        if (BetterWaterConfig.debugMode) {
            logger.info("Fluid blocks initialized: " + fluidBlocks.size() + " fluids registered.");
        }
    }

    public static boolean isFluidBlock(Block block) {
        if (fluidBlocks == null) initFluidBlocks();
        return fluidBlocks.contains(block);
    }

    private static boolean shouldGenerateWaterSourceByRegion(World world, int x, int y, int z) {
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

    // ========== 区域判断（根据流体类型） ==========
    private static boolean shouldGenerateSourceByRegion(World world, int x, int y, int z, boolean isLava) {
        if (isLava) {
            return shouldGenerateLavaSourceByRegion(world, x, y, z);
        } else {
            return shouldGenerateWaterSourceByRegion(world, x, y, z);
        }
    }

    public static boolean shouldGenerateSourceByRegion(World world, int x, int y, int z) {
        return shouldGenerateSourceByRegion(world, x, y, z, false);
    }

    private static boolean shouldGenerateLavaSourceByRegion(World world, int x, int y, int z) {
        if (!BetterWaterConfig.lavaEnabled) return false;
        if (!lavaRegionCheckInitialized) {
            initLavaRegionCheck();
        }
        int dim = world.provider.dimensionId;

        // 强制允许维度
        for (int d : BetterWaterConfig.lavaOceanDims) if (d == dim) return true;

        // 禁止维度
        for (int d : BetterWaterConfig.lavaBannedDims) if (d == dim) return false;

        // Y轴范围
        if (y < BetterWaterConfig.lavaLower || y > BetterWaterConfig.lavaUpper) return false;

        // 群系检测
        BiomeDictionary.Type[] biomes = BiomeDictionary.getTypesForBiome(world.getBiomeGenForCoords(x, z));
        // 禁止群系
        for (BiomeDictionary.Type banned : lavaBannedBiomeTypes) {
            for (BiomeDictionary.Type current : biomes) if (current == banned) return false;
        }

        if (BetterWaterConfig.lavaReverse) return true;

        // 有效群系
        for (BiomeDictionary.Type valid : lavaValidBiomeTypes) {
            for (BiomeDictionary.Type current : biomes) if (current == valid) return true;
        }
        return false;
    }
    // 群系类型数组初始化（类似水的，需新增 lavaValidBiomeTypes 和 lavaBannedBiomeTypes）
    private static BiomeDictionary.Type[] lavaValidBiomeTypes = null;
    private static BiomeDictionary.Type[] lavaBannedBiomeTypes = null;
    private static boolean lavaRegionCheckInitialized = false;

    private static synchronized void initLavaRegionCheck() {
        if (lavaRegionCheckInitialized) return;
        lavaValidBiomeTypes = new BiomeDictionary.Type[BetterWaterConfig.lavaValidBiomeDictionary.length];
        for (int i = 0; i < BetterWaterConfig.lavaValidBiomeDictionary.length; i++) {
            lavaValidBiomeTypes[i] = BiomeDictionary.Type.getType(BetterWaterConfig.lavaValidBiomeDictionary[i]);
        }
        lavaBannedBiomeTypes = new BiomeDictionary.Type[BetterWaterConfig.lavaBannedBiomeDictionary.length];
        for (int i = 0; i < BetterWaterConfig.lavaBannedBiomeDictionary.length; i++) {
            lavaBannedBiomeTypes[i] = BiomeDictionary.Type.getType(BetterWaterConfig.lavaBannedBiomeDictionary[i]);
        }
        lavaRegionCheckInitialized = true;
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        BetterWaterConfig.init(event.getSuggestedConfigurationFile());
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
        logger.info("Better Water mod v2.0 loaded (with lava support)!");
    }

    // init, postInit 省略

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        synchronized (pendingEvents) {
            Queue<PendingBreakEvent> newQueue = new LinkedList<>();
            while (!pendingEvents.isEmpty()) {
                PendingBreakEvent pending = pendingEvents.poll();
                if (pending.delayTicks <= 0) {
                    processBlockBreak(pending);
                } else {
                    pending.delayTicks--;
                    newQueue.add(pending);
                }
            }
            pendingEvents.addAll(newQueue);
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
        Block fluidType = null;
        if (block == Blocks.water && world.getBlockMetadata(x, y, z) == 0) {
            fluidType = Blocks.water;
        } else if (block == Blocks.lava && world.getBlockMetadata(x, y, z) == 0) {
            fluidType = Blocks.lava;
        }
        if (fluidType != null) {
            synchronized (pendingEvents) {
                pendingEvents.add(new PendingBreakEvent(world, x, y, z, true, fluidType));
            }
            if (BetterWaterConfig.debugMode) {
                logger.info(String.format("FillBucketEvent queued at [%d, %d, %d] for %s", x, y, z, fluidType.getLocalizedName()));
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

//        // 简单高度过滤（水海平面附近，岩浆默认不过滤高度，因为由区域检测控制）
//        // 此处可保留水的过滤，岩浆的过滤在区域检测中进行
//        if (BetterWaterConfig.seaLevelRange >= 0) {
//            int seaLevel = 63;
//            if (y < seaLevel - BetterWaterConfig.seaLevelRange || y > seaLevel + BetterWaterConfig.seaLevelRange) {
//                return;
//            }
//        }

        Block brokenBlock = event.block;
        if (!BetterWaterConfig.isBlockAffected(brokenBlock)) return;

        synchronized (pendingEvents) {
            pendingEvents.add(new PendingBreakEvent(world, x, y, z, false, null));
        }
    }

    private static void processBlockBreak(PendingBreakEvent event) {
        World world = event.world;
        int x = event.x;
        int y = event.y;
        int z = event.z;
        boolean fromBucket = event.fromBucket;
        Block specifiedFluid = event.fluidType; // 桶事件指定流体，破坏事件为 null

        Block currentBlock = world.getBlock(x, y, z);
        if (currentBlock != Blocks.air && currentBlock != Blocks.water && currentBlock != Blocks.flowing_water
            && currentBlock != Blocks.lava && currentBlock != Blocks.flowing_lava) {
            return;
        }

        // 桶事件：优先尝试区域检测直接放置
        if (fromBucket && specifiedFluid != null) {
            boolean isLava = (specifiedFluid == Blocks.lava || specifiedFluid == Blocks.flowing_lava);
            if (shouldGenerateSourceByRegion(world, x, y, z, isLava)) {
                world.setBlock(x, y, z, specifiedFluid == Blocks.lava ? Blocks.lava : Blocks.water, 0, 3);
                if (BetterWaterConfig.debugMode) {
                    logger.info(String.format("Placed %s source at [%d, %d, %d] (bucket, region check)",
                        isLava ? "lava" : "water", x, y, z));
                }
                placeFlowingFluid(world, x, y, z, specifiedFluid);
                return;
            }
            // 未通过区域检测，继续水体检测
        }

        // 水平方向检查相邻流体
        int[][] horizontalDirs = { {1,0,0}, {-1,0,0}, {0,0,1}, {0,0,-1} };
        for (int[] dir : horizontalDirs) {
            int adjX = x + dir[0];
            int adjY = y + dir[1];
            int adjZ = z + dir[2];
            Block adjacentBlock = world.getBlock(adjX, adjY, adjZ);
            if (!isFluidBlock(adjacentBlock)) continue;

            // 确定流体类型（水或岩浆）
            boolean isLavaAdj = (adjacentBlock == Blocks.lava || adjacentBlock == Blocks.flowing_lava);
            Block fluidType = isLavaAdj ? Blocks.lava : Blocks.water;

            // 如果来自桶事件且指定了流体，则只处理与该流体一致的相邻流体
            if (fromBucket && specifiedFluid != null && fluidType != specifiedFluid) continue;

            // 检测该流体连通体
            FluidDetectionResult result = detectConnectedFluidBody(world, adjX, adjY, adjZ, fluidType);

            int threshold = isLavaAdj ? BetterWaterConfig.lavaBodyThreshold : BetterWaterConfig.waterBodyThreshold;
            if (result.sourceCount >= threshold) {
                // 检查区域限制
                if (!shouldGenerateSourceByRegion(world, x, y, z, isLavaAdj)) continue;

                world.setBlock(x, y, z, fluidType, 0, 3);
                if (BetterWaterConfig.debugMode) {
                    logger.info(String.format("Placed %s source at [%d, %d, %d]. Body size: %d",
                        isLavaAdj ? "lava" : "water", x, y, z, result.sourceCount));
                }
                placeFlowingFluid(world, x, y, z, fluidType);
                return;
            }
        }
    }

    private static void placeFlowingFluid(World world, int x, int y, int z, Block fluidType) {
        boolean shouldPlace = (fluidType == Blocks.lava) ? BetterWaterConfig.lavaAlwaysCheckAirBlocks : BetterWaterConfig.alwaysCheckAirBlocks;
        if (!shouldPlace) return;
        int[][] directions = { {1,0,0}, {-1,0,0}, {0,0,1}, {0,0,-1}, {0,-1,0} };
        Block flowingBlock = (fluidType == Blocks.lava) ? Blocks.flowing_lava : Blocks.flowing_water;

        for (int[] dir : directions) {
            int adjX = x + dir[0];
            int adjY = y + dir[1];
            int adjZ = z + dir[2];
            if (world.getBlock(adjX, adjY, adjZ) == Blocks.air) {
                world.setBlock(adjX, adjY, adjZ, flowingBlock, 1, 3);
                if (BetterWaterConfig.debugMode) {
                    logger.info(String.format("Placed flowing %s at [%d, %d, %d]",
                        fluidType == Blocks.lava ? "lava" : "water", adjX, adjY, adjZ));
                }
            }
        }
    }

    // 通用流体检测（按类型）
    public static FluidDetectionResult detectConnectedFluidBody(World world, int startX, int startY, int startZ, Block targetFluid) {
        if (fluidBlocks == null) initFluidBlocks();

        Queue<int[]> queue = new LinkedList<>();
        HashSet<String> visited = new HashSet<>();
        int sourceCount = 0;
        int totalCount = 0;

        queue.add(new int[]{startX, startY, startZ});
        visited.add(key(startX, startY, startZ));

        int maxBlocks = (targetFluid == Blocks.lava) ? BetterWaterConfig.lavaMaxBlocksToCheck : BetterWaterConfig.maxBlocksToCheck;
        int searchRad = (targetFluid == Blocks.lava) ? BetterWaterConfig.lavaSearchRadius : BetterWaterConfig.searchRadius;
        int vertRange = (targetFluid == Blocks.lava) ? BetterWaterConfig.lavaMaxVerticalRange : BetterWaterConfig.maxVerticalRange;

        while (!queue.isEmpty() && totalCount < maxBlocks) {
            int[] pos = queue.poll();
            int x = pos[0];
            int y = pos[1];
            int z = pos[2];

            Block block = world.getBlock(x, y, z);
            if (!isFluidBlock(block)) continue;
            // 检查是否为目标流体类型（包括流动）
            boolean matches = (targetFluid == Blocks.lava) ?
                (block == Blocks.lava || block == Blocks.flowing_lava) :
                (block == Blocks.water || block == Blocks.flowing_water);
            if (!matches) continue;

            totalCount++;
            int metadata = world.getBlockMetadata(x, y, z);
            if (metadata == 0) { // 源块
                sourceCount++;
                int threshold = (targetFluid == Blocks.lava) ? BetterWaterConfig.lavaBodyThreshold : BetterWaterConfig.waterBodyThreshold;
                if (sourceCount >= threshold) {
                    return new FluidDetectionResult(sourceCount, totalCount);
                }
            }

            int[][] directions = { {1,0,0}, {-1,0,0}, {0,0,1}, {0,0,-1}, {0,1,0}, {0,-1,0} };
            for (int[] dir : directions) {
                int nx = x + dir[0];
                int ny = y + dir[1];
                int nz = z + dir[2];
                if (Math.abs(nx - startX) > searchRad || Math.abs(nz - startZ) > searchRad || Math.abs(ny - startY) > vertRange) continue;
                String key = key(nx, ny, nz);
                if (!visited.contains(key)) {
                    visited.add(key);
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }
        return new FluidDetectionResult(sourceCount, totalCount);
    }

    // 保留原有 detectConnectedWaterBody 作为兼容（可删除或重定向）
    public static WaterDetectionResult detectConnectedWaterBody(World world, int startX, int startY, int startZ) {
        FluidDetectionResult res = detectConnectedFluidBody(world, startX, startY, startZ, Blocks.water);
        return new WaterDetectionResult(res.sourceCount, res.totalCount);
    }

    private static String key(int x, int y, int z) { return x + ":" + y + ":" + z; }

    private static class PendingBreakEvent {
        final World world;
        final int x, y, z;
        final boolean fromBucket;
        final Block fluidType;
        int delayTicks; // 剩余等待 tick 数

        PendingBreakEvent(World world, int x, int y, int z, boolean fromBucket, Block fluidType) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.fromBucket = fromBucket;
            this.fluidType = fluidType;
            // 桶事件根据流体类型设置延迟，破坏事件延迟为 0（立即处理）
            if (fromBucket && fluidType != null) {
                this.delayTicks = (fluidType == Blocks.lava) ?
                    BetterWaterConfig.lavaProcessingDelayTicks :
                    BetterWaterConfig.processingDelayTicks;
            } else {
                this.delayTicks = 0;
            }
        }
    }

    public static class FluidDetectionResult {
        public final int sourceCount;
        public final int totalCount;
        public FluidDetectionResult(int sourceCount, int totalCount) {
            this.sourceCount = sourceCount;
            this.totalCount = totalCount;
        }
    }

    // 保留 WaterDetectionResult 供外部调用
    public static class WaterDetectionResult {
        public final int sourceCount;
        public final int totalWaterCount;
        public WaterDetectionResult(int sourceCount, int totalWaterCount) {
            this.sourceCount = sourceCount;
            this.totalWaterCount = totalWaterCount;
        }
    }
    /**
     * 从指定位置检测相连水体（如果该位置不是流体，则检查相邻方块）
     * 用于原版水的流动性钩子（BetterWaterHooks）
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
}
