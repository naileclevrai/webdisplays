package net.minecraftforge.fml;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

import java.util.function.Supplier;

public final class ModLoadingContext {
    private static final ModLoadingContext INSTANCE = new ModLoadingContext();

    private ModLoadingContext() {
    }

    public static ModLoadingContext get() {
        return INSTANCE;
    }

    public String getActiveNamespace() {
        return "webdisplays";
    }

    public <T> void registerExtensionPoint(Class<T> type, Supplier<T> supplier) {
        // No-op on Fabric
    }

    public void registerConfig(ModConfig.Type type, ForgeConfigSpec spec, String fileName) {
        // No-op on Fabric
    }
}
