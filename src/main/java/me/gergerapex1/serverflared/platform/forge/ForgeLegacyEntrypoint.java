package me.gergerapex1.serverflared.platform.forge;



//? if forge && legacy_forge {
/*
import me.gergerapex1.serverflared.ModPlatformInstance;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import me.gergerapex1.serverflared.Constants;
@Mod(Constants.MOD_ID)
public class ForgeLegacyEntrypoint {
	@Mod.EventHandler
    public void init(FMLInitializationEvent event) {
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
