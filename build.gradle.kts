
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}
tasks.jar {
    manifest {
        attributes(
            "FMLCorePlugin" to "com.CatFish.BetterWater.BetterWaterCore",
            "FMLCorePluginContainsFMLMod" to "true"
        )
    }
}
