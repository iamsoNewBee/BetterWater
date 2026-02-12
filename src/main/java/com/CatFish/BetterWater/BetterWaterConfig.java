package com.CatFish.BetterWater;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class BetterWaterConfig {

    public static boolean enabled = true;
    public static int waterBodyThreshold = 30;
    public static int searchRadius = 10;
    public static int seaLevelRange = 5;
    public static int maxBlocksToCheck = 1000;
    public static boolean debugMode = false;

    // 新添加：延迟处理tick数
    public static int processingDelayTicks = 1;

    // 新添加：是否总是检查空气方块并放置流动水
    public static boolean alwaysCheckAirBlocks = true;

    private static Configuration config;

    public static void init(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    public static void load() {
        try {
            config.load();

            enabled = config
                .getBoolean("enabled", Configuration.CATEGORY_GENERAL, true, "Enable/disable the Better Water mod");

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

            seaLevelRange = config.getInt(
                "seaLevelRange",
                Configuration.CATEGORY_GENERAL,
                5,
                0,
                30,
                "Vertical range around sea level (63) where water can be generated");

            maxBlocksToCheck = config.getInt(
                "maxBlocksToCheck",
                Configuration.CATEGORY_GENERAL,
                1000,
                10,
                10000,
                "Maximum number of blocks to check when detecting water bodies");

            debugMode = config.getBoolean("debugMode", Configuration.CATEGORY_GENERAL, false, "Enable debug logging");

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

        } catch (Exception e) {
            BetterWater.logger.warning("Failed to load config: " + e.getMessage());
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    public static boolean isBlockAffected(net.minecraft.block.Block block) {
        if (block == null) return false;

        // 默认影响大多数固体方块
        if (block == net.minecraft.init.Blocks.water || block == net.minecraft.init.Blocks.flowing_water
            || block == net.minecraft.init.Blocks.air
            || block == net.minecraft.init.Blocks.bedrock
            || block == net.minecraft.init.Blocks.lava
            || block == net.minecraft.init.Blocks.flowing_lava
            || block == net.minecraft.init.Blocks.obsidian) {
            return false;
        }

        // 你可以在这里添加更多排除的方块
        // 或者切换为白名单模式

        return block.isOpaqueCube();
    }
}
