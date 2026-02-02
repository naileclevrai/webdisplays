package net.minecraftforge.server;

import net.minecraft.server.MinecraftServer;

public final class ServerLifecycleHooks {
    private static volatile MinecraftServer currentServer;

    private ServerLifecycleHooks() {
    }

    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    public static void setCurrentServer(MinecraftServer server) {
        currentServer = server;
    }
}
