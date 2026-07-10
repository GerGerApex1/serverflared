package me.gergerapex1.serverflared;

import me.gergerapex1.serverflared.platform.Platform;

@SuppressWarnings("LoggingSimilarMessage")
public class ModPlatformInstance {
	private static Platform PLATFORM;
	public static void setPlatform(Platform platform) {
		PLATFORM = platform;
	}
	public static Platform xplat() {
		if (PLATFORM == null) {
			throw new RuntimeException("Platform not initialized!");
		}
		return PLATFORM;
	}

	public static void onInitialize() {
		ServerFlared.init();
	}

}
