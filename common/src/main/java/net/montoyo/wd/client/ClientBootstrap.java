package net.montoyo.wd.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.montoyo.wd.client.gui.WebDisplaysConfigScreen;
import net.montoyo.wd.client.gui.camera.KeyboardCamera;
import net.montoyo.wd.config.ClientConfig;

public final class ClientBootstrap {
    private ClientBootstrap() {
    }

    public static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientProxy::onKeybindRegistry);
        MinecraftForge.EVENT_BUS.addListener(ClientProxy::onDrawSelection);
        MinecraftForge.EVENT_BUS.addListener(KeyboardCamera::updateCamera);
        MinecraftForge.EVENT_BUS.addListener(KeyboardCamera::gameTick);
        ClientConfig.init();
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) -> new WebDisplaysConfigScreen(screen)));
    }
}
