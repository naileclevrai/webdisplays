package net.minecraftforge.fml.javafmlmod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public final class FMLJavaModLoadingContext {
    private static final FMLJavaModLoadingContext INSTANCE = new FMLJavaModLoadingContext();
    private final IEventBus modEventBus = MinecraftForge.EVENT_BUS;

    private FMLJavaModLoadingContext() {
    }

    public static FMLJavaModLoadingContext get() {
        return INSTANCE;
    }

    public IEventBus getModEventBus() {
        return modEventBus;
    }
}
