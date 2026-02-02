package me.gergerapex1.serverflared.platform.forge;

//? forge && legacy_forge {
/*import me.gergerapex1.serverflared.platform.Platform;
import me.gergerapex1.serverflared.utils.ClassHelpers;
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
    	// TODO: Add proper implementation for legacy Forge
    	// net.minecraftforge.fml.common.LoaderException:
    	// java.lang.NoSuchMethodError: net.minecraft.server.MinecraftServer.getFile
    	// why????
    	// it exist in the gradle jar file but isn't found at runtime???
        return new File(".").toPath();
    }

    @Override
    public Path getConfigDirectory() {
        return getGameDirectory().resolve("config");
    }

    @Override
    public int getServerPort() {
    	return ClassHelpers.normalizeToServerPort(server);
    }

    @Override
    public String getLocalAddress() {
		return ClassHelpers.normalizeToHostname(server);
    }
}
*///?}
