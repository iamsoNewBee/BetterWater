package com.CatFish.BetterWater;

import java.io.File;
import net.minecraftforge.common.config.Configuration;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

public class BetterWaterConfig {

    // ========== 主要开关 ==========
    public static boolean enabled = true;

    // ========== 水域检测参数 ==========
    public static int waterBodyThreshold = 30;
    public static int searchRadius = 10;
    public static int maxVerticalRange = 0;          // 默认0，只检测同一水平面
    public static int maxBlocksToCheck = 1000;

    // ========== 原版功能扩展参数 ==========
    public static int seaLevelRange = 5;
    public static int processingDelayTicks = 1;
    public static boolean alwaysCheckAirBlocks = true;
    public static boolean debugMode = false;

    // ========== 群系限制配置（水） ==========
    public static int[] validDims = {0};
    public static int[] bannedDims = {-1, 1};
    public static int[] oceanDims = {};
    public static boolean reverse = false;
    public static String[] validBiomeDictionary = {"OCEAN", "BEACH", "RIVER"};
    public static String[] bannedBiomeDictionary = {"NETHER", "END"};
    public static int waterLower = 0;
    public static int waterUpper = 255;

    // ========== 岩浆专用配置 ==========
    public static boolean lavaEnabled = true;
    public static int lavaBodyThreshold = 10;
    public static int lavaSearchRadius = 8;
    public static int lavaMaxVerticalRange = 0;      // 默认0，只检测同一水平面
    public static int lavaMaxBlocksToCheck = 500;
    public static int lavaProcessingDelayTicks = 20; // 默认20 tick
    public static boolean lavaAlwaysCheckAirBlocks = true;

    // 岩浆维度/群系限制
    public static int[] lavaValidDims = {-1};
    public static int[] lavaBannedDims = {0, 1};
    public static int[] lavaOceanDims = {};
    public static boolean lavaReverse = false;
    public static String[] lavaValidBiomeDictionary = {"NETHER"};
    public static String[] lavaBannedBiomeDictionary = {};
    public static int lavaLower = 0;
    public static int lavaUpper = 50;                // 默认最高 Y=50

    private static Configuration config;

    public static void init(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    public static void load() {
        try {
            config.load();

            // 主要开关
            enabled = config.getBoolean("enabled", Configuration.CATEGORY_GENERAL, true, "Enable/disable the Better Water mod");

            // 水域检测参数
            waterBodyThreshold = config.getInt("waterBodyThreshold", Configuration.CATEGORY_GENERAL, 30, 1, 1000, "Minimum number of connected water source blocks required to create a new source");
            searchRadius = config.getInt("searchRadius", Configuration.CATEGORY_GENERAL, 10, 1, 50, "Horizontal search radius for connected water body detection");
            maxBlocksToCheck = config.getInt("maxBlocksToCheck", Configuration.CATEGORY_GENERAL, 1000, 10, 10000, "Maximum number of blocks to check when detecting water bodies");
            maxVerticalRange = config.getInt("maxVerticalRange", Configuration.CATEGORY_GENERAL, 0, 0, 10, "Vertical search range for water bodies (0 = same Y level only)");

            // 原版功能扩展参数
            seaLevelRange = config.getInt("seaLevelRange", Configuration.CATEGORY_GENERAL, 5, 0, 30, "Vertical range around sea level (63) where water can be generated");
            processingDelayTicks = config.getInt("processingDelayTicks", Configuration.CATEGORY_GENERAL, 1, 0, 20, "Number of ticks to wait before processing block break (0 for immediate)");
            alwaysCheckAirBlocks = config.getBoolean("alwaysCheckAirBlocks", Configuration.CATEGORY_GENERAL, true, "Always check for air blocks near broken blocks and place flowing water to trigger updates");
            debugMode = config.getBoolean("debugMode", Configuration.CATEGORY_GENERAL, false, "Enable debug logging");

            // 群系限制参数（水）
            validDims = config.get("general", "validDims", validDims, "Dimension array to allow infinite source water to be created. Unused if 'reversed' is set to true").getIntList();
            bannedDims = config.get("general", "bannedDims", bannedDims, "Dimension array to ban infinite source water to be created (overrides 'validDims' and 'validBiomes')").getIntList();
            oceanDims = config.get("general", "oceanDims", oceanDims, "Dimension array that allows water regeneration regardless of biome (overrides all other config settings)").getIntList();
            reverse = config.getBoolean("reverse", "general", reverse, "If true, water can create infinite sources everywhere except in banned dimensions and biomes");
            validBiomeDictionary = config.getStringList("validBiomes", "general", validBiomeDictionary, "Biome dictionary entries where infinite sources are allowed (e.g., OCEAN, BEACH, RIVER)");
            bannedBiomeDictionary = config.getStringList("bannedBiomes", "general", bannedBiomeDictionary, "Biome dictionary entries where infinite sources are NOT allowed (overrides valid biomes)");
            waterLower = config.getInt("waterLowerBounds", "general", waterLower, 0, 255, "The lowest Y-level where source water can form");
            waterUpper = config.getInt("waterUpperBounds", "general", waterUpper, 0, 255, "The highest Y-level where source water can form");

            // === 岩浆配置 ===
            lavaEnabled = config.getBoolean("lavaEnabled", "lava", true, "Enable infinite lava source generation");
            lavaBodyThreshold = config.getInt("lavaBodyThreshold", "lava", 10, 1, 1000, "Minimum connected lava sources to create new source");
            lavaSearchRadius = config.getInt("lavaSearchRadius", "lava", 8, 1, 50, "Horizontal search radius for lava body");
            lavaMaxVerticalRange = config.getInt("lavaMaxVerticalRange", "lava", 0, 0, 10, "Vertical search range for lava (0 = same Y level only)");
            lavaMaxBlocksToCheck = config.getInt("lavaMaxBlocksToCheck", "lava", 500, 10, 10000, "Max blocks to check for lava");
            lavaProcessingDelayTicks = config.getInt("lavaProcessingDelayTicks", "lava", 20, 0, 20, "Delay before processing lava block break");
            lavaAlwaysCheckAirBlocks = config.getBoolean("lavaAlwaysCheckAirBlocks", "lava", true, "Place flowing lava around new source");

            lavaValidDims = config.get("lava", "lavaValidDims", lavaValidDims, "Dimensions where lava sources can form").getIntList();
            lavaBannedDims = config.get("lava", "lavaBannedDims", lavaBannedDims, "Dimensions where lava sources are banned").getIntList();
            lavaOceanDims = config.get("lava", "lavaOceanDims", lavaOceanDims, "Dimensions where lava always allowed").getIntList();
            lavaReverse = config.getBoolean("lavaReverse", "lava", lavaReverse, "If true, lava allowed everywhere except banned dims/biomes");

            lavaValidBiomeDictionary = config.getStringList("lavaValidBiomes", "lava", lavaValidBiomeDictionary, "Biome dictionary tags where lava sources allowed");
            lavaBannedBiomeDictionary = config.getStringList("lavaBannedBiomes", "lava", lavaBannedBiomeDictionary, "Biome dictionary tags where lava sources banned");

            lavaLower = config.getInt("lavaLowerBounds", "lava", lavaLower, 0, 255, "Lowest Y-level for lava source formation");
            lavaUpper = config.getInt("lavaUpperBounds", "lava", lavaUpper, 0, 255, "Highest Y-level for lava source formation");

        } catch (Exception e) {
            BetterWater.logger.warning("Failed to load config: " + e.getMessage());
        } finally {
            if (config.hasChanged()) config.save();
        }
    }

    /**
     * 判断指定方块是否受模组影响（即破坏后可能触发水源生成）
     */
    public static boolean isBlockAffected(Block block) {
        if (block == null) return false;
        if (block == Blocks.water || block == Blocks.flowing_water
            || block == Blocks.air
            || block == Blocks.bedrock
            || block == Blocks.lava
            || block == Blocks.flowing_lava
            || block == Blocks.obsidian) {
            return false;
        }
        return block.isOpaqueCube();
    }
}
