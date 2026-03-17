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
        NeoForge.EVENT_BUS.addListener(NeoforgeEntrypoint::serverStarting);
        NeoForge.EVENT_BUS.addListener(NeoforgeEntrypoint::serverStopping);
		NeoForge.EVENT_BUS.addListener(NeoforgeEntrypoint::serverStarted);
	}
    private static void serverStarting(ServerStartingEvent event) {
        ServerFlared.init();
    	ModPlatformInstance.onInitialize();
        ServerFlared.handleTunnel();
    }
    private static void serverStopping(ServerStoppingEvent event) {
        ServerFlared.cleanup();
    }
    private static void serverStarted(ServerStartedEvent event) { ServerFlared.startedServer(); }
}
*///?}
