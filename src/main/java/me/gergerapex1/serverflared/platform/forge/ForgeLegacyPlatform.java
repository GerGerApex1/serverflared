package me.gergerapex1.serverflared.platform.forge;

//? forge && legacy_forge {
/*import me.gergerapex1.serverflared.platform.Platform;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;


public class ForgeLegacyPlatform implements Platform {

	@Override
	public boolean isModLoaded(String modId) {
		return Loader.isModLoaded(modId);
	}

	private MinecraftServer server;
    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        server = event.getServer();
    }
    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        server = null;
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return false;
    }
	@Override
    public ModLoader getPlatformName() {
        return ModLoader.FORGE;
    }

    @Override
    public Path getGameDirectory() {
        return server.getDataDirectory().toPath();
    }

    @Override
    public Path getConfigDirectory() {
        return server.getDataDirectory().toPath().resolve("config");
    }

    @Override
    public int getServerPort() {
    	// see stonecutter.gradle.kts
    	//$ mc_1_10_2_port
    	return server.getPort();
    }

    @Override
    public String getLocalAddress() {
    	// see stonecutter.gradle.kts
    	//$ mc_1_10_2_hostname
        return server.getHostname();
    }
}
*///?}
