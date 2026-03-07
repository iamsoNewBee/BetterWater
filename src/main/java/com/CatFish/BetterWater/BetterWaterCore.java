package com.CatFish.BetterWater;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({ "com.CatFish.BetterWater" })
public class BetterWaterCore implements IFMLLoadingPlugin {

    static {
        System.out.println("BetterWaterCore loaded!");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { BetterWaterTransformer.class.getName() };
    }

    @Override
    public String getModContainerClass() {
        return null; // 使用普通 @Mod 容器，无需额外容器
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
