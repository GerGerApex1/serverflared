package me.gergerapex1.serverflared.platform.fabric;

//? fabric {

import java.nio.file.Path;
import me.gergerapex1.serverflared.platform.Platform;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

public class FabricPlatform implements Platform {

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	private static MinecraftServer server = null;
	public static void initialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {;
			FabricPlatform.server = server;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FabricPlatform.server = null;
		});
		//FabricLoader.getInstance()
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
//?}
