package me.gergerapex1.serverflared.platform.forge;

//? if forge && !legacy_forge  {

/*
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.ModPlatformInstance;
import me.gergerapex1.serverflared.ServerFlared;

@Mod(Constants.MOD_ID)
public class ForgeModernEntrypoint {

	public ForgeModernEntrypoint() {
		ModPlatformInstance.onInitialize();
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
    public void serverStarted(ServerStartedEvent event) { ServerFlared.postServerStart(); }
}
*///?}
