package me.gergerapex1.serverflared.platform.forge;

//? forge && !legacy_forge {
/*import me.gergerapex1.serverflared.platform.Platform;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;

public class ForgeModernPlatform implements Platform {

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}
	private MinecraftServer server;
    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        server = event.getServer();
    }
    @EventHandler
    public void serverStopping() {
        server = null;
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
    public ModLoader getPlatformName() {
        return ModLoader.FORGE;
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
