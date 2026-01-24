//~ forge_imports_modern
package me.gergerapex1.serverflared.platform.forge;

//? if forge && !legacy_forge  {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.ModPlatformInstance;
import me.gergerapex1.serverflared.ServerFlared;

@Mod(Constants.MOD_ID)
public class ForgeModernEntrypoint {
	public void init(FMLCommonSetupEvent event) {
		ModPlatformInstance.onInitialize();
		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(ForgeModernPlatform.class);
	}
	@SubscribeEvent
    public void serverStarting(FMLServerStartingEvent event) {
        ServerFlared.handleTunnel();
    }
    @SubscribeEvent
    public void serverStopping(FMLServerStoppingEvent event) {
        ServerFlared.cleanup();
    }
    @SubscribeEvent
    public void serverStarted(FMLServerStartedEvent event) { ServerFlared.startedServer(); }
}
*///?}
