package me.gergerapex1.serverflared.platform.spigot;

import me.gergerapex1.serverflared.ModPlatformInstance;
import me.gergerapex1.serverflared.ServerFlared;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

public class SpigotEntrypoint extends JavaPlugin {
	@Override
	public void onEnable() {
		ModPlatformInstance.setPlatform(new SpigotPlatform(this));
		ModPlatformInstance.onInitialize();
		//? if legacySpigot {
		Bukkit.getScheduler().runTaskLater(this, new Runnable() {
			@Override
			public void run() {
				ServerFlared.startedServer();
			}
		}, 40L);

		//?} else {
		/*Bukkit.getPluginManager().registerEvents(new SpigotListeners(), this);
		*///? }
	}

	@Override
	public void onDisable() {
		ServerFlared.cleanup();
	}

}
