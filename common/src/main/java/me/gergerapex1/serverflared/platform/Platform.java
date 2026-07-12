package me.gergerapex1.serverflared.platform;

import java.nio.file.Path;

public interface Platform {
	/**
	 * Checks if a mod with the given id is loaded.
	 *
	 * @param modId The mod to check if it is loaded.
	 * @return True if the mod is loaded, false otherwise.
	 */
	boolean isModLoaded(String modId);

	/**
	 * Check if the game is currently in a development environment.
	 *
	 * @return True if in a development environment, false otherwise.
	 */
	boolean isDevelopmentEnvironment();

	/**
	 * Gets the name of the environment type as a string.
	 *
	 * @return The name of the environment type.
	 */
	default String getEnvironmentName() {
		return isDevelopmentEnvironment() ? "development" : "production";
	}
	/**
	 * Gets the game directory as a Path.
	 *
	 * @return The path of the game directory.
	 */
	Path getGameDirectory();
	/**
	 * Gets the "config" directory as a Path.
	 *
	 * @return The path of the config directory.
	 */
	Path getConfigDirectory();
	/**
	 * Gets the server port of the Minecraft server that is running on.
	 *
	 * @return Returns the port as Int
	 */
	int getServerPort();
	/**
	 * Gets the local address of the Minecraft server that is running on.
	 *
	 * @return Returns the local address as String
	 */
	String getLocalAddress();
	ModLoader getPlatformName();

	enum ModLoader {
		FABRIC, NEOFORGE, FORGE, QUILT, SPIGOT
	}
}
