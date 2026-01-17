package me.gergerapex1.serverflared.platform.forge;

//? if forge && legacy_forge {
/*import me.gergerapex1.serverflared.ModPlatformInstance;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.ServerFlared;
@Mod(modid = Constants.MOD_ID)
public class ForgeLegacyEntrypoint {
	@EventHandler
    public void init(FMLInitializationEvent event) {
		ModPlatformInstance.onInitialize();
		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(ForgeLegacyPlatform.class);
	}
	@EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		ServerFlared.handleTunnel();
	}
	@EventHandler
	public void serverStopping(FMLServerStoppingEvent event) {
		ServerFlared.cleanup();
	}
	@EventHandler
	public void serverStarted(FMLServerStartedEvent event) {
		ServerFlared.postServerStart();
	}

}
*///?}
