package com.CatFish.BetterWater;

import net.minecraft.world.World;

public class BetterWaterHooks {

    public static boolean shouldGenerateSource(World world, int x, int y, int z) {
        if (!BetterWaterConfig.enabled) {
            return true; // 模组禁用时恢复原版行为
        }
        BetterWater.WaterDetectionResult result = BetterWater.detectConnectedWaterBodyFromPos(world, x, y, z);
        boolean allow = result.sourceCount >= BetterWaterConfig.waterBodyThreshold;
        if (BetterWaterConfig.debugMode) {
            System.out.println(
                "[BetterWater] shouldGenerateSource at (" + x
                    + ","
                    + y
                    + ","
                    + z
                    + ") sourceCount="
                    + result.sourceCount
                    + " allow="
                    + allow);
        }
        return allow;
    }
}
