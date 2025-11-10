package me.gergerapex1.serverflared;

import me.gergerapex1.serverflared.platform.FabricPlatformHelper;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class Serverflared implements ModInitializer {
    
    @Override
    public void onInitialize() {
        
        // This method is invoked by the Fabric mod loader when it is ready
        // to load your mod. You can access Fabric and Common code in this
        // project.

        // Use Fabric to bootstrap the Common mod.
        FabricPlatformHelper.initialize();
        CommonClass.init();
        ServerLifecycleEvents.SERVER_STARTING.register(event -> {
            CommonClass.serverStarting();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(event -> {
            CommonClass.cleanup();
        });
    }
}
