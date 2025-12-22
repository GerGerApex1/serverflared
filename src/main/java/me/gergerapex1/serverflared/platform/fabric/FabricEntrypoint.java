package me.gergerapex1.serverflared.platform.fabric;

//? fabric {

import me.gergerapex1.serverflared.ModPlatformInstance;
import me.gergerapex1.serverflared.ServerFlared;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class FabricEntrypoint implements ModInitializer {

	@Override
	public void onInitialize() {
		ModPlatformInstance.onInitialize();
		FabricPlatform.initialize();
		ServerLifecycleEvents.SERVER_STARTING.register(event -> ServerFlared.handleTunnel());
		ServerLifecycleEvents.SERVER_STOPPING.register(event -> ServerFlared.cleanup());
		ServerLifecycleEvents.SERVER_STARTED.register(event -> ServerFlared.postServerStart());
	}
}
//?}
