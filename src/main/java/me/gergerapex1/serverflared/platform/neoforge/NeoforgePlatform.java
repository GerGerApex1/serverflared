package me.gergerapex1.serverflared.platform.neoforge;

//? neoforge {
/*import me.gergerapex1.serverflared.platform.Platform;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class NeoforgePlatform implements Platform {
	private static final MinecraftServer serverHook = ServerLifecycleHooks.getCurrentServer();
	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}
    @Override
    public ModLoader getPlatformName() {
        return ModLoader.NEOFORGE;
    }


    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
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
