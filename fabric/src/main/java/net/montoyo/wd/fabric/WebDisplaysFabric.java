package net.montoyo.wd.fabric;

import net.fabricmc.api.ModInitializer;
import net.montoyo.wd.WebDisplays;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WebDisplaysFabric implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("webdisplays");

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(ServerLifecycleHooks::setCurrentServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ServerLifecycleHooks.setCurrentServer(null));
        new WebDisplays();
    }
}
