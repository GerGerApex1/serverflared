package me.gergerapex1.serverflared.platform.spigot;

import java.nio.file.Path;
import me.gergerapex1.serverflared.platform.Platform;
import org.bukkit.plugin.Plugin;

public class SpigotPlatform implements Platform {
	private final Plugin plugin;
	public SpigotPlatform(Plugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean isModLoaded(String pluginId) {
		return plugin != null && plugin.getServer().getPluginManager().isPluginEnabled(pluginId);
	}

	@Override
	public boolean isDevelopmentEnvironment() {
		return false;
	}

	@Override
	public Path getGameDirectory() {
		return plugin.getServer().getWorldContainer().toPath().toAbsolutePath();
	}

	@Override
	public Path getConfigDirectory() {
		return plugin.getDataFolder().toPath().toAbsolutePath();
	}

	@Override
	public int getServerPort() {
		return plugin.getServer().getPort();
	}

	@Override
	public String getLocalAddress() {
		return plugin.getServer().getIp();
	}

	@Override
	public ModLoader getPlatformName() {
		return ModLoader.SPIGOT;
	}
}
