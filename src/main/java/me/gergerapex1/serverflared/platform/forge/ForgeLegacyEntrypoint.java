package me.gergerapex1.serverflared.platform.forge;



//? if forge && (minecraft: <= 1.12) {
/*
import me.gergerapex1.serverflared.ModPlatformInstance;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;

public class ForgeLegacyEntrypoint {

	public ForgeLegacyEntrypoint() {
		ModPlatformInstance.onInitialize();
	}
	@Mod.EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		ServerFlared.handleTunnel();
	}
	@Mod.EventHandler
	public void serverStopping(FMLServerStoppingEvent event) {
		ServerFlared.cleanup();
	}
	@Mod.EventHandler
	public void serverStarted(FMLServerStartedEvent event) { ServerFlared.postServerStart(); }
}
*///?}
