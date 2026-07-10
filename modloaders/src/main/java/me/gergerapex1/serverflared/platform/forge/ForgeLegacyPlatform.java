package me.gergerapex1.serverflared.platform.forge;

//? forge && legacy_forge {
/*import me.gergerapex1.serverflared.platform.Platform;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;
import java.io.File;
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
        return Loader.instance().getConfigDir().getParentFile().toPath();
    }

    @Override
    public Path getConfigDirectory() {
        return Loader.instance().getConfigDir().toPath();
    }

	@Override
	public int getServerPort() {
		return server != null ? server.getServerPort() : 25565;
	}
	@Override
	public String getLocalAddress() {
		return server != null ? server.getServerHostname() : "0.0.0.0";
	}
}
*///?}
