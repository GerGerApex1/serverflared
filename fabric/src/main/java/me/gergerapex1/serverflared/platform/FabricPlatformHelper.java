package me.gergerapex1.serverflared.platform;

import java.nio.file.Path;
import me.gergerapex1.serverflared.platform.services.IPlatformHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

public class FabricPlatformHelper implements IPlatformHelper {
    private static MinecraftServer server = null;
    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {;
            FabricPlatformHelper.server = server;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            FabricPlatformHelper.server = null;
        });
        //FabricLoader.getInstance()
    }
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path getGameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public int getServerPort() {
        return server.getPort();
    }

    @Override
    public String getLocalAddress() {
        return server.getLocalIp();
    }
}
