package me.gergerapex1.serverflared.platform.spigot;

//? if !legacySpigot {
/*import me.gergerapex1.serverflared.ServerFlared;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.server.ServerLoadEvent.LoadType;

public class SpigotListeners implements Listener {
	@EventHandler
	public void onServerLoad(ServerLoadEvent event) {
		if(event.getType().equals(LoadType.STARTUP)) {
			ServerFlared.startedServer();
		}
	}
}

*///?}
