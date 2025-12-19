package me.gergerapex1.serverflared.platform.forge;

//? forge {

/*import me.gergerapex1.serverflared.platform.Platform;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import me.gergerapex1.serverflared.ServerFlared;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.nio.file.Path;
import me.gergerapex1.serverflared.Constants;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.loading.FMLLoader;

public class ForgePlatform implements Platform {

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	private MinecraftServer server;
    @SubscribeEvent
    public void serverStarting(ServerStartingEvent event) {
        server = event.getServer();
    }
    @SubscribeEvent
    public void serverStopping() {
        server = null;
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
*///?}
