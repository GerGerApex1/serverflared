package me.gergerapex1.serverflared.platform;

import java.nio.file.Path;
import me.gergerapex1.serverflared.platform.services.IPlatformHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public class NeoForgePlatformHelper implements IPlatformHelper {
    private static MinecraftServer server;
    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent event) {
        server = event.getServer();
    }
    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        server = null;
    }
    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public Path getGameDirectory() {
        return server.getServerDirectory();
    }

    @Override
    public Path getConfigDirectory() {
        return server.getServerDirectory().resolve("config");
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
