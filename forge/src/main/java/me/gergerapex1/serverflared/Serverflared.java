package me.gergerapex1.serverflared;

import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class Serverflared {

    public Serverflared() {
        // This method is invoked by the Forge mod loader when it is ready
        // to load your mod. You can access Forge and Common code in this
        // project.

        // Use Forge to bootstrap the Common mod.
        Constants.LOG.info("Hello Forge world!");
        CommonClass.init();
    }
    @SubscribeEvent
    public void serverStarting(ServerStartingEvent event) {
        CommonClass.serverStarting();
    }
    @SubscribeEvent
    public void serverStopping(ServerStoppingEvent event) {
        CommonClass.cleanup();
    }
}
