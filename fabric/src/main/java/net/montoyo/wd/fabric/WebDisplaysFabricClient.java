package net.montoyo.wd.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.montoyo.wd.client.ClientProxy;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class WebDisplaysFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(ClientProxy.KEY_MOUSE);
        ClientProxy.onClientSetup(new FMLClientSetupEvent());
    }
}
