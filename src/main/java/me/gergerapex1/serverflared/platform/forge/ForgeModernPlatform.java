//~ forge_imports_modern
package me.gergerapex1.serverflared.platform.forge;

//? forge && !legacy_forge {
/*import java.io.File;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.platform.Platform;
import me.gergerapex1.serverflared.utils.ClassHelpers;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.eventbus.api.SubscribeEvent;
//TODO: import proper development environment check
//import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerLifecycleEvent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

public class ForgeModernPlatform implements Platform {
	private static MinecraftServer serverHook = ServerLifecycleHooks.getCurrentServer();
	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}
    @Override
    public boolean isDevelopmentEnvironment() {
        //return !FMLLoader.isProduction();
        return false;
    }
    @Override
    public Path getGameDirectory() {
		//? if >1.21 {
		return serverHook != null ? serverHook.getServerDirectory() : Path.of(".");
		//? } else {
		/^return serverHook != null ? serverHook.getServerDirectory().toPath() : Path.of(".");
		^///? }
    }
	@Override
    public ModLoader getPlatformName() {
        return ModLoader.FORGE;
    }
    @Override
    public Path getConfigDirectory() {
        return getGameDirectory().resolve("config");
    }
    @Override
    public int getServerPort() {
		return serverHook != null ? server.getPort() : 25565;
    }
    @Override
    public String getLocalAddress() {
        return serverHook != null ? server.getLocalIp() : "0.0.0.0";
    }
}
*///?}
