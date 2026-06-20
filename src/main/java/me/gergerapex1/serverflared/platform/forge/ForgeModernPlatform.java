//~ forge_imports_modern
package me.gergerapex1.serverflared.platform.forge;

//? forge && !legacy_forge {
/*import me.gergerapex1.serverflared.platform.Platform;
import net.minecraftforge.fml.ModList;
//TODO: import proper development environment check
//import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraft.server.MinecraftServer;
import java.nio.file.Path;
//$ fml_serverlifecyclehooks_1_18
import net.minecraftforge.server.ServerLifecycleHooks;
public class ForgeModernPlatform implements Platform {
	private static MinecraftServer serverHook = ServerLifecycleHooks.getCurrentServer();
	@Override
	public boolean isModLoaded(String modId) {
		//$ fml_deobfuscated_isModLoaded
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
		return serverHook != null ? serverHook.getPort() : 25565;
    }
    @Override
    public String getLocalAddress() {
        return serverHook != null ? serverHook.getLocalIp() : "0.0.0.0";
    }
}
*///?}
