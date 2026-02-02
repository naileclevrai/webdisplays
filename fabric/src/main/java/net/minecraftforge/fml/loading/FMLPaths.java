package net.minecraftforge.fml.loading;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public enum FMLPaths {
    CONFIGDIR,
    GAMEDIR;

    public Path get() {
        return switch (this) {
            case CONFIGDIR -> FabricLoader.getInstance().getConfigDir();
            case GAMEDIR -> FabricLoader.getInstance().getGameDir();
        };
    }
}
