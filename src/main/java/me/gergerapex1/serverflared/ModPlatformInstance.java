package me.gergerapex1.serverflared;

import me.gergerapex1.serverflared.platform.Platform;
//? fabric {
import me.gergerapex1.serverflared.platform.fabric.FabricPlatform;
//?} neoforge {
/*import me.gergerapex1.serverflared.platform.neoforge.NeoforgePlatform;
 *///?} forge && (minecraft: >= 1.13) {
/*import me.gergerapex1.serverflared.platform.forge.ForgeModernPlatform;
 *///?} forge && (minecraft: <= 1.12) {
 /*import me.gergerapex1.serverflared.platform.forge.ForgeLegacyPlatform;
  *///?}

@SuppressWarnings("LoggingSimilarMessage")
public class ModPlatformInstance {

	private static final Platform PLATFORM = createPlatformInstance();

	public static void onInitialize() {
		ServerFlared.init();
	}

	public static Platform xplat() {
		return PLATFORM;
	}

	private static Platform createPlatformInstance() {
		//? fabric {
		return new FabricPlatform();
		//?} neoforge {
		/*return new NeoforgePlatform();
		 *///?} forge && !legacy_forge {
		/*return new ForgeModernPlatform();
		 *///?} forge && legacy_forge {
		 /*return new ForgeLegacyPlatform();
		  *///?}
	}
}
