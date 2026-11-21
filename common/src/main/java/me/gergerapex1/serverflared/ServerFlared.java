package me.gergerapex1.serverflared;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import me.gergerapex1.serverflared.cloudflared.handler.CloudFlaredHandler;
import me.gergerapex1.serverflared.cloudflared.handler.LocalManagedTunnel;
import me.gergerapex1.serverflared.cloudflared.handler.ManagedTunnel;
import me.gergerapex1.serverflared.cloudflared.handler.RemoteManagedTunnel;
import me.gergerapex1.serverflared.cloudflared.handler.TunnelInfo;
import me.gergerapex1.serverflared.cloudflared.handler.TunnelMode;
import me.gergerapex1.serverflared.cloudflared.handler.TunnelState;
import me.gergerapex1.serverflared.command.CommandSenderAdapter;
import me.gergerapex1.serverflared.utils.config.Config;
import me.gergerapex1.serverflared.utils.config.ConfigManager;

// This class is part of the common project meaning it is shared between all supported loaders. Code written here can only
// import and access the vanilla codebase, libraries used by vanilla, and optionally third party libraries that provide
// common compatible binaries. This means common code can not directly use loader specific concepts such as Forge events
// however it will be compatible with all supported mod loaders.
public class ServerFlared {
    private static ConfigManager configManager;
    private static CloudFlaredHandler handler;
    private static ManagedTunnel activeTunnel;
    private static volatile boolean modDisabled = false;
    private static final AtomicBoolean tunnelInitStarted = new AtomicBoolean(false);
    private static final AtomicBoolean loginInProgress = new AtomicBoolean(false);
    private static final Object LIFECYCLE_LOCK = new Object();
    private static long lifecycleGeneration = 0L;
    private static long preparedGeneration = -1L;
    private static long launchedGeneration = -1L;
    private static ManagedTunnel preparedTunnel;
    private static Config preparedConfig;
    private static volatile boolean serverStarted = false;
    private static volatile boolean pendingTunnelStart = false;
    private static volatile boolean tunnelPrepared = false;
    private static volatile boolean firstTimeMode = false;
    private static volatile TunnelInfo info = new TunnelInfo();
    private static volatile Runnable commandTreeSyncCallback;

    private static final AtomicInteger ASYNC_THREAD_COUNTER = new AtomicInteger(1);
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "serverflared-async-" + ASYNC_THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    public static void init() {
        Constants.LOG.info("Initializing {}", Constants.MOD_FRIENDLY_NAME);
        synchronized (LIFECYCLE_LOCK) {
            lifecycleGeneration++;
            serverStarted = false;
            pendingTunnelStart = false;
            tunnelPrepared = false;
            preparedGeneration = -1L;
            launchedGeneration = -1L;
            preparedTunnel = null;
            preparedConfig = null;
            tunnelInitStarted.set(false);
            firstTimeMode = false;
            modDisabled = false;

            configManager = new ConfigManager();
            handler = CloudFlaredHandler.createInstance();
            TunnelMode mode = getConfiguredMode();
            if (handler != null) {
                activeTunnel = createTunnelHandler(mode, handler);
            }

            if (configManager.firstTime || ConfigManager.isPendingFirstTimeSetup(configManager.CONFIG)) {
                firstTimeMode = true;
                modDisabled = true;
                logFirstTimeSetup(mode);
                return;
            }
            if (handler == null || activeTunnel == null) {
                Constants.LOG.error("Failed to create CloudFlaredHandler instance, mod disabled.");
                modDisabled = true;
                return;
            }
            if (activeTunnel.requiresCliAuth() && !handler.isAuthenticated()) {
                modDisabled = true;
                final long authGeneration = lifecycleGeneration;
                // Prevent mod from starting until authentication is done
                handler.authenticate().thenAccept(v -> {
                    if (Boolean.TRUE.equals(v)) {
                        synchronized (LIFECYCLE_LOCK) {
                            if (lifecycleGeneration != authGeneration) {
                                Constants.LOG.debug("Ignoring stale authentication completion for generation {}.", authGeneration);
                                return;
                            }
                        }
                        Constants.LOG.info("Authentication completed, resuming mod operation.");
                        resumeAfterAuthentication();
                    } else {
                        Constants.LOG.info("Authentication process went wrong");
                    }
                });
            }
        }
    }

    private static void resumeAfterAuthentication() {
        boolean shouldPrepareAndLaunch;
        synchronized (LIFECYCLE_LOCK) {
            modDisabled = false;
            shouldPrepareAndLaunch = !firstTimeMode && (pendingTunnelStart || serverStarted);
        }
        if (shouldPrepareAndLaunch) {
            handleTunnel();
            if (serverStarted) {
                launchPreparedTunnelIfReady();
            }
        }
    }

    private static TunnelMode getConfiguredMode() {
        if (configManager == null || configManager.CONFIG == null) {
            return TunnelMode.LOCAL;
        }
        return configManager.CONFIG.getResolvedTunnelMode();
    }

    private static ManagedTunnel createTunnelHandler(TunnelMode mode, CloudFlaredHandler cfHandler) {
        if (mode != null && mode.isRemote()) {
            return new RemoteManagedTunnel(cfHandler, mode);
        }
        return new LocalManagedTunnel(cfHandler);
    }

    private static void logFirstTimeSetup(TunnelMode mode) {
        Path configPath = ModPlatformInstance.xplat().getConfigDirectory()
            .resolve(Constants.CONFIG_DIR)
            .resolve("config.yml");

        Constants.LOG.info("First time setup detected. Please configure {}", configPath.toString());
        Constants.LOG.info("Supported tunnelMode options:");
        Constants.LOG.info("  - LOCAL (default): Set 'tunnelName' and 'hostName', then authenticate via cloudflared login.");
        Constants.LOG.info("  - REMOTE_TOKEN: Set 'tunnelMode: REMOTE_TOKEN' and 'remote.tunnelToken' (or SERVERFLARED_TUNNEL_TOKEN env var). "
            + "Also set 'hostName' to your existing Cloudflare Dashboard route before generating a player command with /serverflared clientcmd.");
        Constants.LOG.info("  - REMOTE_API: Set 'tunnelMode: REMOTE_API', 'hostName', and 'remote.apiToken' (or SERVERFLARED_API_TOKEN env var).");
        Constants.LOG.info("After configuring, please restart the server or run /serverflared reload.");
        if (mode == TunnelMode.LOCAL && handler != null && !handler.isAuthenticated()) {
            Constants.LOG.info("For LOCAL mode, you also need to authenticate cloudflared via /serverflared login.");
        }
        Constants.LOG.info("Mod disabled until configured.");
    }

    public static void handleTunnel() {
        if (firstTimeMode) {
            Constants.LOG.debug("First-time setup mode active; tunnel start skipped.");
            return;
        }

        if (modDisabled) {
            pendingTunnelStart = true;
            Constants.LOG.debug("handleTunnel requested while mod disabled; deferring until authenticated.");
            return;
        }

        if (!tunnelInitStarted.compareAndSet(false, true)) {
            Constants.LOG.debug("Tunnel already initiated; skipping duplicate call.");
            return;
        }

        Constants.LOG.debug("Handling tunnel now (mode={})...", getConfiguredMode());
        boolean prepared = initiateTunnel();
        if (!prepared) {
            tunnelInitStarted.set(false);
            Constants.LOG.error("Tunnel preparation failed; tunnel will not start automatically. Check logs and run /serverflared reload or /serverflared start.");
        }
    }

    public static void startedServer() {
        synchronized (LIFECYCLE_LOCK) {
            serverStarted = true;
            if (firstTimeMode) {
                return;
            }
            if (modDisabled) {
                pendingTunnelStart = true;
                Constants.LOG.debug("startedServer reached while authentication is pending; deferring tunnel launch.");
                return;
            }
        }
        launchPreparedTunnelIfReady();
    }

    private static void launchPreparedTunnelIfReady() {
        final long expectedGeneration;
        final ManagedTunnel targetTunnel;
        final Config targetConfig;
        final TunnelInfo targetInfo;

        synchronized (LIFECYCLE_LOCK) {
            if (modDisabled || activeTunnel == null || configManager == null || configManager.CONFIG == null) {
                return;
            }
            if (!tunnelPrepared
                || preparedGeneration != lifecycleGeneration
                || preparedTunnel != activeTunnel
                || preparedConfig != configManager.CONFIG) {
                Constants.LOG.error("Skipping tunnel startup because tunnel preparation did not succeed for the current configuration.");
                return;
            }
            if (launchedGeneration == lifecycleGeneration || activeTunnel.isTunnelRunning()) {
                Constants.LOG.debug("Tunnel launch already queued or running for current lifecycle generation.");
                return;
            }
            expectedGeneration = lifecycleGeneration;
            launchedGeneration = expectedGeneration;
            targetTunnel = activeTunnel;
            targetConfig = configManager.CONFIG;
            targetInfo = info;
            pendingTunnelStart = false;
        }

        Constants.LOG.debug("Starting tunnel in background (mode={})...", targetTunnel.getMode());
        CompletableFuture.runAsync(() -> runTunnelBackground(expectedGeneration, targetTunnel, targetConfig, targetInfo), ASYNC_EXECUTOR);
    }

    public static void cleanup() {
        Constants.LOG.info("Stopping serverflared");
        Constants.LOG.info("Stopping all processes...");
        synchronized (LIFECYCLE_LOCK) {
            lifecycleGeneration++;
            serverStarted = false;
            pendingTunnelStart = false;
            tunnelPrepared = false;
            preparedGeneration = -1L;
            launchedGeneration = -1L;
            preparedTunnel = null;
            preparedConfig = null;
            tunnelInitStarted.set(false);
            if (handler != null) {
                handler.terminateProcesses();
            }
            if (activeTunnel != null) {
                activeTunnel.terminateProcesses();
            }
        }
        Constants.LOG.info("Successfully stopped serverflared. See you next time!");
    }

    public static void runTunnelBackground() {
        final long expectedGeneration;
        final ManagedTunnel targetTunnel;
        final Config targetConfig;
        final TunnelInfo targetInfo;
        synchronized (LIFECYCLE_LOCK) {
            expectedGeneration = lifecycleGeneration;
            targetTunnel = activeTunnel;
            targetConfig = configManager != null ? configManager.CONFIG : null;
            targetInfo = info;
        }
        runTunnelBackground(expectedGeneration, targetTunnel, targetConfig, targetInfo);
    }

    private static void runTunnelBackground(
        long expectedGeneration,
        ManagedTunnel targetTunnel,
        Config targetConfig,
        TunnelInfo targetInfo
    ) {
        if (targetTunnel == null || targetConfig == null) {
            return;
        }
        boolean started;
        synchronized (LIFECYCLE_LOCK) {
            if (lifecycleGeneration != expectedGeneration
                || modDisabled
                || activeTunnel != targetTunnel
                || configManager == null
                || configManager.CONFIG != targetConfig
                || !tunnelPrepared
                || preparedGeneration != expectedGeneration) {
                Constants.LOG.debug("Skipping stale tunnel launch for generation {}.", expectedGeneration);
                return;
            }
            started = targetTunnel.startTunnel(targetConfig, targetInfo);
            if (!started) {
                launchedGeneration = -1L;
            }
        }
        if (started) {
            String host = targetConfig.getHostName();
            if (host != null && !host.isEmpty() && !ConfigManager.isDefaultHostName(host)) {
                Constants.LOG.info("Tunnel started! You can join the server with " + host);
            } else {
                Constants.LOG.info("Tunnel started in {} mode!", targetTunnel.getMode());
            }
        } else {
            Constants.LOG.error("Failed to start cloudflared tunnel ({}). Check server logs for details.", targetTunnel.getMode());
        }
    }

    private static boolean initiateTunnel() {
        final long targetGeneration;
        final CloudFlaredHandler targetHandler;
        final ManagedTunnel targetTunnel;
        final Config targetConfig;

        synchronized (LIFECYCLE_LOCK) {
            if (handler == null || activeTunnel == null || configManager == null || configManager.CONFIG == null) {
                return false;
            }
            if (activeTunnel.requiresCliAuth() && !handler.isAuthenticated()) {
                return false;
            }
            targetGeneration = lifecycleGeneration;
            targetHandler = handler;
            targetTunnel = activeTunnel;
            targetConfig = configManager.CONFIG;
        }

        TunnelInfo prepared = targetTunnel.prepareTunnel(
            targetConfig,
            ModPlatformInstance.xplat().getLocalAddress(),
            ModPlatformInstance.xplat().getServerPort()
        );
        if (prepared == null) {
            synchronized (LIFECYCLE_LOCK) {
                if (lifecycleGeneration == targetGeneration && activeTunnel == targetTunnel) {
                    tunnelPrepared = false;
                    preparedGeneration = -1L;
                    preparedTunnel = null;
                    preparedConfig = null;
                }
            }
            return false;
        }

        synchronized (LIFECYCLE_LOCK) {
            if (lifecycleGeneration != targetGeneration
                || handler != targetHandler
                || activeTunnel != targetTunnel
                || configManager == null
                || configManager.CONFIG != targetConfig) {
                Constants.LOG.debug("Discarding stale tunnel preparation result for generation {}.", targetGeneration);
                return false;
            }
            info = prepared;
            tunnelPrepared = true;
            preparedGeneration = targetGeneration;
            preparedTunnel = targetTunnel;
            preparedConfig = targetConfig;
            updateConfigWithTunnelInfo(targetConfig, prepared);
            return true;
        }
    }

    private static void updateConfigWithTunnelInfo(Config targetConfig, TunnelInfo tunnel) {
        if (tunnel == null || targetConfig == null || configManager == null || configManager.CONFIG != targetConfig) {
            return;
        }
        boolean changed = false;
        if (tunnel.getId() != null && !tunnel.getId().isEmpty()
            && !tunnel.getId().equals(targetConfig.getTunnelId())) {
            targetConfig.setTunnelId(tunnel.getId());
            changed = true;
        }
        if (tunnel.getName() != null && !tunnel.getName().isEmpty()
            && !tunnel.getName().equals(targetConfig.getTunnelName())) {
            targetConfig.setTunnelName(tunnel.getName());
            changed = true;
        }
        if (changed) {
            configManager.saveConfig();
        }
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }

    public static CloudFlaredHandler getHandler() {
        return handler;
    }

    public static ManagedTunnel getActiveTunnel() {
        return activeTunnel;
    }

    public static LocalManagedTunnel getLocalHandler() {
        return (activeTunnel instanceof LocalManagedTunnel) ? (LocalManagedTunnel) activeTunnel : null;
    }

    public static TunnelInfo getTunnelInfo() {
        return info;
    }

    public static boolean isConsoleOnlyMode() {
        if (configManager == null || configManager.CONFIG == null) {
            return false;
        }
        return configManager.CONFIG.isConsoleOnly();
    }

    public static void setCommandTreeSyncCallback(Runnable callback) {
        commandTreeSyncCallback = callback;
    }

    public static void notifyCommandTreeSync() {
        Runnable cb = commandTreeSyncCallback;
        if (cb != null) {
            try {
                cb.run();
            } catch (Throwable t) {
                Constants.LOG.debug("Failed to sync command tree", t);
            }
        }
    }

    public static CompletableFuture<Void> startTunnelAsync(final CommandSenderAdapter sender) {
        return CompletableFuture.runAsync(() -> {
            synchronized (LIFECYCLE_LOCK) {
                if (activeTunnel != null && activeTunnel.isTunnelRunning()) {
                    long pid = activeTunnel.getActivePid();
                    sender.sendMessage("\u00A7e[ServerFlared] Tunnel is already running" + (pid > 0 ? " (PID: " + pid + ")" : "") + ".");
                    return;
                }
                if (handler == null) {
                    handler = CloudFlaredHandler.createInstance();
                }
                if (handler == null) {
                    sender.sendMessage("\u00A7c[ServerFlared] Failed to initialize cloudflared binary handler.");
                    return;
                }
                TunnelMode mode = getConfiguredMode();
                if (activeTunnel == null || activeTunnel.getMode() != mode) {
                    activeTunnel = createTunnelHandler(mode, handler);
                }
                if (activeTunnel.requiresCliAuth() && !handler.isAuthenticated()) {
                    sender.sendMessage("\u00A7c[ServerFlared] Cloudflared is not authenticated. Run /serverflared login first.");
                    return;
                }
                sender.sendMessage("\u00A7e[ServerFlared] Starting cloudflared tunnel (" + mode.name() + ")...");
                activeTunnel.setState(TunnelState.STARTING);
                activeTunnel.setLastConnectionDetail("Initiating tunnel configuration...");
                boolean prepared = initiateTunnel();
                if (!prepared) {
                    sender.sendMessage("\u00A7c[ServerFlared] Failed to prepare tunnel: " + activeTunnel.getLastConnectionDetail());
                    return;
                }
                boolean started = activeTunnel.startTunnel(configManager.CONFIG, info);
                if (started) {
                    launchedGeneration = lifecycleGeneration;
                    modDisabled = false;
                    firstTimeMode = false;
                    sender.sendMessage("\u00A7a[ServerFlared] Tunnel started successfully (" + mode.name() + ") for \u00A7f"
                        + configManager.CONFIG.getHostName());
                } else {
                    sender.sendMessage("\u00A7c[ServerFlared] Failed to start cloudflared tunnel. Check server logs for details.");
                }
            }
        }, ASYNC_EXECUTOR);
    }

    public static CompletableFuture<Void> stopTunnelAsync(final CommandSenderAdapter sender) {
        return CompletableFuture.runAsync(() -> {
            synchronized (LIFECYCLE_LOCK) {
                if (activeTunnel == null || !activeTunnel.isTunnelRunning()) {
                    if (activeTunnel != null) {
                        activeTunnel.setState(TunnelState.STOPPED);
                    }
                    sender.sendMessage("\u00A7e[ServerFlared] Tunnel is already stopped.");
                    return;
                }
                sender.sendMessage("\u00A7e[ServerFlared] Stopping cloudflared tunnel...");
                lifecycleGeneration++;
                launchedGeneration = -1L;
                activeTunnel.terminateProcesses();
                sender.sendMessage("\u00A7a[ServerFlared] Cloudflared tunnel stopped.");
            }
        }, ASYNC_EXECUTOR);
    }

    public static CompletableFuture<Void> reloadAsync(final CommandSenderAdapter sender) {
        return CompletableFuture.runAsync(() -> {
            synchronized (LIFECYCLE_LOCK) {
                sender.sendMessage("\u00A7e[ServerFlared] Reloading config.yml and validating tunnel configuration...");
                if (configManager == null) {
                    configManager = new ConfigManager();
                }

                // Validate candidate configuration and all prerequisites BEFORE stopping the active tunnel.
                Config candidateConfig = configManager.loadCandidateConfig();
                if (candidateConfig == null) {
                    sender.sendMessage("\u00A7c[ServerFlared] Failed to reload config.yml. Active tunnel left unchanged.");
                    return;
                }

                TunnelMode candidateMode = candidateConfig.getResolvedTunnelMode();
                CloudFlaredHandler candidateHandler = (handler != null) ? handler : CloudFlaredHandler.createInstance();
                if (candidateHandler == null) {
                    sender.sendMessage("\u00A7c[ServerFlared] Failed to initialize cloudflared binary handler. Active tunnel left unchanged.");
                    return;
                }

                ManagedTunnel candidateTunnel = createTunnelHandler(candidateMode, candidateHandler);
                if (candidateTunnel.requiresCliAuth() && !candidateHandler.isAuthenticated()) {
                    sender.sendMessage(
                        "\u00A7e[ServerFlared] Reload aborted: cloudflared is not authenticated for LOCAL mode. "
                            + "Run /serverflared login first. Active tunnel left unchanged."
                    );
                    return;
                }
                if (ConfigManager.isPendingFirstTimeSetup(candidateConfig)) {
                    sender.sendMessage(
                        "\u00A7e[ServerFlared] Reload aborted: required settings are incomplete for "
                            + candidateMode.name() + " mode. Update config.yml, then run /serverflared reload. Active tunnel left unchanged."
                    );
                    return;
                }

                candidateTunnel.setState(TunnelState.STARTING);
                candidateTunnel.setLastConnectionDetail("Preparing reloaded tunnel configuration...");
                TunnelInfo candidateInfo = candidateTunnel.prepareTunnel(
                    candidateConfig,
                    ModPlatformInstance.xplat().getLocalAddress(),
                    ModPlatformInstance.xplat().getServerPort()
                );
                if (candidateInfo == null) {
                    sender.sendMessage(
                        "\u00A7c[ServerFlared] Reload aborted: failed to prepare tunnel ("
                            + candidateTunnel.getLastConnectionDetail() + "). Active tunnel left unchanged."
                    );
                    return;
                }

                // Candidate is validated and prepared; now swap out the active tunnel safely.
                sender.sendMessage("\u00A77[ServerFlared] Config validated (tunnelMode=" + candidateMode.name()
                    + ", commandPermissionMode=" + candidateConfig.getCommandPermissionMode()
                    + "). Switching tunnel...");

                lifecycleGeneration++;
                if (activeTunnel != null) {
                    activeTunnel.terminateProcesses();
                }

                handler = candidateHandler;
                configManager.applyConfig(candidateConfig);
                activeTunnel = candidateTunnel;
                info = candidateInfo;
                tunnelPrepared = true;
                preparedGeneration = lifecycleGeneration;
                preparedTunnel = candidateTunnel;
                preparedConfig = candidateConfig;
                launchedGeneration = -1L;
                updateConfigWithTunnelInfo(candidateConfig, candidateInfo);
                notifyCommandTreeSync();

                boolean started = candidateTunnel.startTunnel(candidateConfig, candidateInfo);
                if (started) {
                    launchedGeneration = lifecycleGeneration;
                    modDisabled = false;
                    firstTimeMode = false;
                    sender.sendMessage("\u00A7a[ServerFlared] Reload complete! Tunnel running (" + candidateMode.name()
                        + ") for \u00A7f" + candidateConfig.getHostName());
                } else {
                    sender.sendMessage("\u00A7c[ServerFlared] Config reloaded, but failed to start cloudflared tunnel.");
                }
            }
        }, ASYNC_EXECUTOR);
    }

    public static CompletableFuture<Void> loginAsync(final CommandSenderAdapter sender) {
        return CompletableFuture.runAsync(() -> {
            TunnelMode mode = getConfiguredMode();
            if (mode.isRemote()) {
                sender.sendMessage("\u00A7e[ServerFlared] CLI login (cert.pem) is not required in "
                    + mode.name() + " mode. Configure remote settings in config.yml and run /serverflared reload.");
                return;
            }
            if (handler == null) {
                handler = CloudFlaredHandler.createInstance();
            }
            if (handler == null) {
                sender.sendMessage("\u00A7c[ServerFlared] Failed to initialize cloudflared binary handler.");
                return;
            }
            if (handler.isAuthenticated()) {
                sender.sendMessage("\u00A7a[ServerFlared] Cloudflared is already authenticated (cert.pem exists).");
                return;
            }
            if (!loginInProgress.compareAndSet(false, true)) {
                sender.sendMessage("\u00A7e[ServerFlared] Cloudflared login is already in progress. Check console/chat for the login link.");
                return;
            }
            sender.sendMessage("\u00A7e[ServerFlared] Launching 'cloudflared tunnel login'... Waiting for authorization URL...");
            handler.authenticate(url -> {
                if (!sender.isConsole()) {
                    sender.sendClickableUrl(
                        "\u00A7a[ServerFlared] \u00A7eClick here to authenticate Cloudflared: \u00A7b\u00A7n" + url,
                        url
                    );
                } else {
                    sender.sendMessage("[ServerFlared] Authenticate Cloudflared at: " + url);
                }
            }).whenComplete((authenticated, throwable) -> {
                loginInProgress.set(false);
                if (throwable != null || !Boolean.TRUE.equals(authenticated)) {
                    sender.sendMessage("\u00A7c[ServerFlared] Cloudflared authentication failed or timed out.");
                } else {
                    sender.sendMessage("\u00A7a[ServerFlared] Cloudflared authentication completed successfully!");
                    resumeAfterAuthentication();
                }
            });
        }, ASYNC_EXECUTOR);
    }
}
