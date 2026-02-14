//~ forge_imports_modern
package me.gergerapex1.serverflared.platform.forge;

//? if forge && !legacy_forge  {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
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
    public void serverStarting(ServerStartingEvent event) {
        ServerFlared.handleTunnel();
    }
    @SubscribeEvent
    public void serverStopping(ServerStoppingEvent event) {
        ServerFlared.cleanup();
    }
    @SubscribeEvent
    public void serverStarted(ServerStartedEvent event) { ServerFlared.startedServer(); }
}
*///?}
