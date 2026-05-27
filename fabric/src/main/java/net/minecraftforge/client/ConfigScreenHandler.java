package net.minecraftforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.BiFunction;

public final class ConfigScreenHandler {
    private ConfigScreenHandler() {
    }

    public static final class ConfigScreenFactory {
        private final BiFunction<Minecraft, Screen, Screen> factory;

        public ConfigScreenFactory(BiFunction<Minecraft, Screen, Screen> factory) {
            this.factory = factory;
        }

        public BiFunction<Minecraft, Screen, Screen> getFactory() {
            return factory;
        }
    }
}
