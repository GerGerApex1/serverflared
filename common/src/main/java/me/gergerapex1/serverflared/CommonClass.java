package me.gergerapex1.serverflared;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import me.gergerapex1.serverflared.cloudflared.handler.CloudFlaredHandler;
import me.gergerapex1.serverflared.cloudflared.handler.LocalManagedTunnel;
import me.gergerapex1.serverflared.cloudflared.handler.TunnelInfo;
import me.gergerapex1.serverflared.platform.Services;
import me.gergerapex1.serverflared.utils.config.ConfigManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

// This class is part of the common project meaning it is shared between all supported loaders. Code written here can only
// import and access the vanilla codebase, libraries used by vanilla, and optionally third party libraries that provide
// common compatible binaries. This means common code can not directly use loader specific concepts such as Forge events
// however it will be compatible with all supported mod loaders.
public class CommonClass {
    private static ConfigManager configManager;
    private static CloudFlaredHandler handler;
    private static LocalManagedTunnel localHandler;
    private static TunnelInfo info = new TunnelInfo();
    public static void init() {
        Constants.LOG.info("Initializing {}", Constants.MOD_NAME);
        Configurator.setAllLevels(Constants.LOG.getName(), Level.DEBUG);
        configManager = new ConfigManager();
        handler = CloudFlaredHandler.createInstance();
        localHandler = new LocalManagedTunnel(handler);
        //Download.binary(ArchVersions.WINDOWS_AMD64.getArchiveName(), ArchVersions.WINDOWS_AMD64.getArchiveName(), Paths.get(Services.PLATFORM.getGameDirectory().toString(), "binaries").toString());
        if (configManager.firstTime) {
            logFirstTimeSetup();
            return;
        }
        if (handler == null) {
            Constants.LOG.error("Failed to create CloudFlaredHandler instance, mod disabled.");
            return;
        }
        if (!handler.isAuthenticated()) {
            CompletableFuture.runAsync(() -> handler.authenticate());
        }
    }
    
    private static void logFirstTimeSetup() {
        Path configPath = Services.PLATFORM.getConfigDirectory()
            .resolve(Constants.CONFIG_DIR)
            .resolve("config.yml");
        
        Constants.LOG.info("First time setup detected, please configure the TUNNEL NAME (\"tunnelName\") "
            + "and HOSTNAME (\"hostname\") in the config file generated at {}", configPath.toString());
        Constants.LOG.info("If you have existing cloudflared tunnel or remotely managed tunnel, please configure the TUNNEL ID "
            + "(\"tunnelID\") instead");
        Constants.LOG.info("After configuring the tunnel name, please restart the server.");
        Constants.LOG.info("Mod disabled.");
    }
    public static void serverStarting() {
        Constants.LOG.info("Handling tunnel now...");
        initiateTunnel();
        Constants.LOG.info("Starting tunnel in background...");
        CompletableFuture.runAsync(CommonClass::runTunnelBackground);
        Constants.LOG.info("Tunnel started!");
        Constants.LOG.info("Your tunnel URL is: {}", configManager.CONFIG.getHostname());
    }
    public static void cleanup() {
        Constants.LOG.info("Stopping all processes...");
        localHandler.terminate();
    }
    public static void runTunnelBackground() {
        localHandler.runLocalTunnel(info.getId());
    }
    private static void initiateTunnel() {
        // Check if tunnel exist with the config
        TunnelInfo initialTunnelInfo = new TunnelInfo();
        initialTunnelInfo.setName(configManager.CONFIG.getTunnelName());
        initialTunnelInfo.setId(configManager.CONFIG.getTunnelId());
        if(handler.getTunnelInfo(initialTunnelInfo) == null) {
            Constants.LOG.info("Tunnel with name {} and ID {} not found, creating new tunnel", initialTunnelInfo.getName(), initialTunnelInfo.getId());

            TunnelInfo newTunnelId = localHandler.createTunnel(initialTunnelInfo);
            localHandler.routeDnsToTunnel(newTunnelId.getId(), configManager.CONFIG.getHostname());
            initialTunnelInfo = newTunnelId;

            configManager.CONFIG.setTunnelId(newTunnelId.getId());
            configManager.saveConfig();

            Constants.LOG.info("New tunnel created with ID {}", newTunnelId);
        } else {
            Constants.LOG.info("Tunnel with name {} and ID {} found", initialTunnelInfo.getName(), initialTunnelInfo.getId());
        }
        info = initialTunnelInfo;
    }
}
