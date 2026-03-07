package com.CatFish.BetterWater;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class BetterWaterConfig {

    // ========== 主要开关 ==========
    /** 是否启用模组（true=启用，false=禁用，恢复原版行为） */
    public static boolean enabled = true;

    // ========== 水域检测参数 ==========
    /** 连通完整水源的最小数量，达到此值才允许生成新水源（默认50） */
    public static int waterBodyThreshold = 50;

    /** 水平搜索半径（单位：方块），用于限制水域检测范围 */
    public static int searchRadius = 10;

    /** 垂直搜索范围（单位：方块），仅检测起始点上下各1格 */
    public static int maxVerticalRange = 1; //

    /** 最大检测方块数，防止性能问题（默认1000） */
    public static int maxBlocksToCheck = 1000;

    // ========== 原版功能扩展参数 ==========
    /** 海平面垂直范围，仅在该范围内生效（海平面63 ± seaLevelRange） */
    public static int seaLevelRange = 1;

    /** 方块破坏事件处理延迟（tick数），0为立即处理 */
    public static int processingDelayTicks = 1;

    /** 是否在生成水源时检查周围空气并放置流动水以触发更新 */
    public static boolean alwaysCheckAirBlocks = true;

    /** 调试模式（输出详细日志） */
    public static boolean debugMode = false;

    private static Configuration config;

    public static void init(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    public static void load() {
        try {
            config.load();

            // 主要开关
            enabled = config
                .getBoolean("enabled", Configuration.CATEGORY_GENERAL, true, "Enable/disable the Better Water mod");

            // 水域检测参数
            waterBodyThreshold = config.getInt(
                "waterBodyThreshold",
                Configuration.CATEGORY_GENERAL,
                30,
                1,
                1000,
                "Minimum number of connected water source blocks required to create a new source");

            searchRadius = config.getInt(
                "searchRadius",
                Configuration.CATEGORY_GENERAL,
                10,
                1,
                50,
                "Horizontal search radius for connected water body detection");

            maxBlocksToCheck = config.getInt(
                "maxBlocksToCheck",
                Configuration.CATEGORY_GENERAL,
                1000,
                10,
                10000,
                "Maximum number of blocks to check when detecting water bodies");

            // 原版功能扩展参数
            seaLevelRange = config.getInt(
                "seaLevelRange",
                Configuration.CATEGORY_GENERAL,
                5,
                0,
                30,
                "Vertical range around sea level (63) where water can be generated");

            processingDelayTicks = config.getInt(
                "processingDelayTicks",
                Configuration.CATEGORY_GENERAL,
                1,
                0,
                20,
                "Number of ticks to wait before processing block break (0 for immediate)");

            alwaysCheckAirBlocks = config.getBoolean(
                "alwaysCheckAirBlocks",
                Configuration.CATEGORY_GENERAL,
                true,
                "Always check for air blocks near broken blocks and place flowing water to trigger updates");

            debugMode = config.getBoolean("debugMode", Configuration.CATEGORY_GENERAL, false, "Enable debug logging");

        } catch (Exception e) {
            BetterWater.logger.warning("Failed to load config: " + e.getMessage());
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    /**
     * 判断指定方块是否受模组影响（即破坏后可能触发水源生成）
     */
    public static boolean isBlockAffected(net.minecraft.block.Block block) {
        if (block == null) return false;

        // 排除水、流动水、空气、基岩、岩浆、流动岩浆、黑曜石等
        if (block == net.minecraft.init.Blocks.water || block == net.minecraft.init.Blocks.flowing_water
            || block == net.minecraft.init.Blocks.air
            || block == net.minecraft.init.Blocks.bedrock
            || block == net.minecraft.init.Blocks.lava
            || block == net.minecraft.init.Blocks.flowing_lava
            || block == net.minecraft.init.Blocks.obsidian) {
            return false;
        }

        // 默认只影响不透明方块（固体）
        return block.isOpaqueCube();
    }
}
