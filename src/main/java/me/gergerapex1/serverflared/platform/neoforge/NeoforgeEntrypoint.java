package me.gergerapex1.serverflared.platform.neoforge;

//? neoforge {
/*import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.fml.common.Mod;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.ModPlatformInstance;
import me.gergerapex1.serverflared.ServerFlared;

@Mod(Constants.MOD_ID)
public class NeoforgeEntrypoint {
	public NeoforgeEntrypoint() {
		ModPlatformInstance.onInitialize();
		Constants.LOG.info("Hello NeoForge world!");
        NeoForge.EVENT_BUS.addListener(NeoforgePlatform::serverStarting);
        NeoForge.EVENT_BUS.addListener(NeoforgePlatform::serverStopping);
        NeoForge.EVENT_BUS.addListener(NeoforgeEntrypoint::serverStarting);
        NeoForge.EVENT_BUS.addListener(NeoforgeEntrypoint::serverStopping);
        ServerFlared.init();
	}
    private static void serverStarting(ServerStartingEvent event) {
        ServerFlared.handleTunnel();
    }
    private static void serverStopping(ServerStartingEvent event) {
        ServerFlared.cleanup();
    }
    private static void serverStarted(ServerStartedEvent event) { ServerFlared.startedServer(); }
}
*///?}
