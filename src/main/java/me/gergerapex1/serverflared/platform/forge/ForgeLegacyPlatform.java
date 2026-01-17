package me.gergerapex1.serverflared.platform.forge;

//? forge && legacy_forge {

/*import me.gergerapex1.serverflared.platform.Platform;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.loading.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraft.server.MinecraftServer;
import me.gergerapex1.serverflared.ServerFlared;
import me.gergerapex1.serverflared.Constants;
import java.nio.file.Path;


public class ForgeLegacyPlatform implements Platform {

	@Override
	public boolean isModLoaded(String modId) {
		return Loader.isModLoaded(modId);
	}

	private MinecraftServer server;
    @SubscribeEvent
    public void serverStarting(FMLServerStartingEvent event) {
        server = event.getServer();
    }
    @Mod.EventHandler
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
        return server.getServerDirectory().toPath();
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
