package me.gergerapex1.serverflared;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import me.gergerapex1.serverflared.cloudflared.binaries.Download;
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
    private static boolean modDisabled = false;
    private static TunnelInfo info = new TunnelInfo();
    public static void init() {
        Constants.LOG.info("Initializing {}", Constants.MOD_NAME);
        Configurator.setAllLevels(Constants.LOG.getName(), Level.DEBUG);
        configManager = new ConfigManager();
        handler = CloudFlaredHandler.createInstance();
        localHandler = new LocalManagedTunnel(handler);
        if (configManager.firstTime) {
            logFirstTimeSetup();
            modDisabled = true;
            return;
        }
        if (handler == null) {
            Constants.LOG.error("Failed to create CloudFlaredHandler instance, mod disabled.");
            modDisabled = true;
            return;
        }
        if (!handler.isAuthenticated()) {
            modDisabled = true;
            // Prevent mod from starting until authentication is done
            CompletableFuture.runAsync(() -> handler.authenticate()).thenAccept(v -> {
                Constants.LOG.info("Authentication completed, resuming mod operation.");
                modDisabled = false;
                handleTunnel();
            });
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
    public static void handleTunnel() {
        if(modDisabled) {
            return;
        }
        Constants.LOG.info("Handling tunnel now...");
        initiateTunnel();
    }
    public static void postServerStart() {
        if(modDisabled) {
            return;
        }
        Constants.LOG.info("Starting tunnel in background...");
        localHandler.createTunnelConfig(info.getId(), Services.PLATFORM.getLocalAddress(), Services.PLATFORM.getServerPort());
        CompletableFuture.runAsync(CommonClass::runTunnelBackground);
        Constants.LOG.info("Tunnel started!");
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
        boolean isTunnelExist = handler.validateTunnelExist(initialTunnelInfo);
        Constants.LOG.info("Is tunnel exist: {}", isTunnelExist);
        if(isTunnelExist) {
            Constants.LOG.info("Tunnel with name {} and ID {} not found, creating new tunnel", initialTunnelInfo.getName(), initialTunnelInfo.getId());

            TunnelInfo createdTunnel = localHandler.createTunnel(initialTunnelInfo);
            if (createdTunnel == null) {
                Constants.LOG.error("Failed to create tunnel for name {} and ID {}. Aborting tunnel initiation.", initialTunnelInfo.getName(), initialTunnelInfo.getId());
                return;
            }
            localHandler.routeDnsToTunnel(createdTunnel.getId(), configManager.CONFIG.getSubdomain());

            updateConfigWithTunnelInfo(createdTunnel);

            initialTunnelInfo = createdTunnel;
            Constants.LOG.info("New tunnel created with ID {}", createdTunnel.getId());
        } else {
            TunnelInfo existingTunnel = handler.getTunnelInfo(initialTunnelInfo);
            Constants.LOG.info("Tunnel with name {} and ID {} found", existingTunnel.getName(), existingTunnel.getId());
            initialTunnelInfo = existingTunnel;
        }

        info = initialTunnelInfo;
    }
    private static void updateConfigWithTunnelInfo(TunnelInfo tunnel) {
        configManager.CONFIG.setTunnelId(tunnel.getId());
        configManager.CONFIG.setTunnelName(tunnel.getName());
        configManager.saveConfig();
    }

    public static void main(String[] args) {

    }
}
