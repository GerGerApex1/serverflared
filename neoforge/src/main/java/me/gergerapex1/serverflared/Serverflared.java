package me.gergerapex1.serverflared;


import me.gergerapex1.serverflared.platform.NeoForgePlatformHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(Constants.MOD_ID)
public class Serverflared {

    public Serverflared(IEventBus eventBus) {
        // This method is invoked by the NeoForge mod loader when it is ready
        // to load your mod. You can access NeoForge and Common code in this
        // project.

        // Use NeoForge to bootstrap the Common mod.
        Constants.LOG.info("Hello NeoForge world!");
        NeoForge.EVENT_BUS.addListener(NeoForgePlatformHelper::serverStarting);
        NeoForge.EVENT_BUS.addListener(NeoForgePlatformHelper::serverStopping);
        NeoForge.EVENT_BUS.addListener(Serverflared::serverStarting);
        NeoForge.EVENT_BUS.addListener(Serverflared::serverStopping);
        CommonClass.init();
    }
    private static void serverStarting(ServerStartingEvent event) {
        CommonClass.serverStarting();
    }
    private static void serverStopping(ServerStartingEvent event) {
        CommonClass.cleanup();
    }
}
