//~ forge_imports_modern
package me.gergerapex1.serverflared.platform.forge;

//? forge && !legacy_forge {
/*import me.gergerapex1.serverflared.platform.Platform;
import me.gergerapex1.serverflared.utils.ClassHelpers;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
//TODO: import proper development environment check
//import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;

public class ForgeModernPlatform implements Platform {

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}
	private MinecraftServer server;
    @SubscribeEvent
    public void serverStarting(FMLServerStartingEvent event) {
        server = event.getServer();
    }
    @SubscribeEvent
    public void serverStopping() {
        server = null;
    }
    @Override
    public boolean isDevelopmentEnvironment() {
        //return !FMLLoader.isProduction();
        return false;
    }
    @Override
    public Path getGameDirectory() {
		Object serverDirectory = server.getServerDirectory();
		return ClassHelpers.normalizeToPath(serverDirectory);
    }
	@Override
    public ModLoader getPlatformName() {
        return ModLoader.FORGE;
    }
    @Override
    public Path getConfigDirectory() {
        return getGameDirectory().resolve("config");
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
