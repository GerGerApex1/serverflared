package me.gergerapex1.serverflared.platform.neoforge;

//? neoforge {
/*import me.gergerapex1.serverflared.platform.Platform;
import java.nio.file.Path;
import me.gergerapex1.serverflared.Constants;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public class NeoforgePlatform implements Platform {

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	private static MinecraftServer server;
    public static void serverStarting(ServerStartingEvent event) {
        server = event.getServer();
    }
    public static void serverStopping(ServerStoppingEvent event) {
        server = null;
    }
    @Override
    public ModLoader getPlatformName() {
        return ModLoader.NEOFORGE;
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
*///?}
