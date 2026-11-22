package me.gergerapex1.serverflared;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
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
    private static volatile ConfigManager configManager;
    private static volatile CloudFlaredHandler handler;
    private static volatile ManagedTunnel activeTunnel;
    private static volatile boolean modDisabled = false;
    private static final AtomicBoolean tunnelInitStarted = new AtomicBoolean(false);
    private static final AtomicReference<Object> activeLoginAttempt = new AtomicReference<>(null);
    // Lock ordering invariant: whenever both locks are acquired, WRITE_OWNER_LOCK MUST be acquired before LIFECYCLE_LOCK.
    private static final Object WRITE_OWNER_LOCK = new Object();
    private static final Object LIFECYCLE_LOCK = new Object();
    private static final AtomicLong lifecycleGeneration = new AtomicLong(0L);
    private static final AtomicLong writeAttemptSeq = new AtomicLong(0L);
    private static long activeWriteOwner = 0L;
    private static final long RELOAD_EDGE_CONNECT_TIMEOUT_MS = 15_000L;
    private static Object startingStateOwner = null;
    private static final ConcurrentHashMap<Object, ManagedTunnel> IN_FLIGHT_PREPARATIONS = new ConcurrentHashMap<>();
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
            long initGeneration = lifecycleGeneration.incrementAndGet();
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
                final long authGeneration = initGeneration;
                // Prevent mod from starting until authentication is done
                handler.authenticate(null, () -> lifecycleGeneration.get() == authGeneration).thenAccept(v -> {
                    if (Boolean.TRUE.equals(v)) {
                        Constants.LOG.info("Authentication completed, resuming mod operation.");
                        resumeAfterAuthentication(authGeneration);
                    } else {
                        Constants.LOG.info("Authentication process went wrong");
                    }
                });
            }
        }
    }

    private static void resumeAfterAuthentication(long authGeneration) {
        boolean shouldPrepare;
        synchronized (LIFECYCLE_LOCK) {
            if (lifecycleGeneration.get() != authGeneration) {
                Constants.LOG.debug("Ignoring stale authentication completion for generation {}.", authGeneration);
                return;
            }
            modDisabled = false;
            shouldPrepare = !firstTimeMode && (pendingTunnelStart || serverStarted);
        }
        if (shouldPrepare) {
            handleTunnelForGeneration(authGeneration);
            synchronized (LIFECYCLE_LOCK) {
                if (lifecycleGeneration.get() != authGeneration || !serverStarted) {
                    return;
                }
            }
            launchPreparedTunnelIfReady();
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

    private static ManagedTunnel createReloadCandidateTunnelHandler(TunnelMode mode, CloudFlaredHandler cfHandler, long attemptId) {
        if (mode != null && mode.isRemote()) {
            return new RemoteManagedTunnel(cfHandler, mode);
        }
        return new LocalManagedTunnel(cfHandler, LocalManagedTunnel.buildStagedConfigPath(attemptId));
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
        long currentGeneration;
        synchronized (LIFECYCLE_LOCK) {
            if (firstTimeMode) {
                Constants.LOG.debug("First-time setup mode active; tunnel start skipped.");
                return;
            }
            if (modDisabled) {
                pendingTunnelStart = true;
                Constants.LOG.debug("handleTunnel requested while mod disabled; deferring until authenticated.");
                return;
            }
            currentGeneration = lifecycleGeneration.get();
        }
        handleTunnelForGeneration(currentGeneration);
    }

    private static void handleTunnelForGeneration(long expectedGeneration) {
        synchronized (LIFECYCLE_LOCK) {
            if (lifecycleGeneration.get() != expectedGeneration || firstTimeMode || modDisabled) {
                return;
            }
        }

        if (!tunnelInitStarted.compareAndSet(false, true)) {
            Constants.LOG.debug("Tunnel already initiated; skipping duplicate call.");
            return;
        }

        Constants.LOG.debug("Handling tunnel now (mode={}, gen={})...", getConfiguredMode(), expectedGeneration);
        boolean prepared = initiateTunnel(expectedGeneration);
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
            long currentGen = lifecycleGeneration.get();
            if (modDisabled || activeTunnel == null || configManager == null || configManager.CONFIG == null) {
                return;
            }
            if (!tunnelPrepared
                || preparedGeneration != currentGen
                || preparedTunnel != activeTunnel
                || preparedConfig != configManager.CONFIG) {
                Constants.LOG.error("Skipping tunnel startup because tunnel preparation did not succeed for the current configuration.");
                return;
            }
            if (launchedGeneration == currentGen || activeTunnel.isTunnelRunning()) {
                Constants.LOG.debug("Tunnel launch already queued or running for current lifecycle generation.");
                return;
            }
            expectedGeneration = currentGen;
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

        // Invalidate pending launches & cancel any in-flight preparations immediately WITHOUT acquiring LIFECYCLE_LOCK.
        lifecycleGeneration.incrementAndGet();
        terminateTrackedProcessesLockFree();

        synchronized (LIFECYCLE_LOCK) {
            lifecycleGeneration.incrementAndGet();
            serverStarted = false;
            pendingTunnelStart = false;
            tunnelPrepared = false;
            preparedGeneration = -1L;
            launchedGeneration = -1L;
            preparedTunnel = null;
            preparedConfig = null;
            tunnelInitStarted.set(false);
            activeLoginAttempt.set(null);
            startingStateOwner = null;
            terminateTrackedProcessesLockFree();
        }
        Constants.LOG.info("Successfully stopped serverflared. See you next time!");
    }

    private static void terminateInFlightPreparations() {
        Set<ManagedTunnel> uniqueTunnels = Collections.newSetFromMap(new IdentityHashMap<ManagedTunnel, Boolean>());
        for (ManagedTunnel preparing : IN_FLIGHT_PREPARATIONS.values()) {
            if (preparing != null) {
                uniqueTunnels.add(preparing);
            }
        }
        for (ManagedTunnel preparing : uniqueTunnels) {
            preparing.terminateProcesses();
        }
    }

    private static void terminateTrackedProcessesLockFree() {
        CloudFlaredHandler currentHandler = handler;
        if (currentHandler != null) {
            currentHandler.terminateProcesses();
        }
        terminateInFlightPreparations();
        ManagedTunnel currentTunnel = activeTunnel;
        if (currentTunnel != null) {
            currentTunnel.terminateProcesses();
        }
    }

    private static boolean claimOrVerifyWriteOwner(long attemptId, long baseGeneration) {
        if (lifecycleGeneration.get() != baseGeneration) {
            return false;
        }
        if (activeWriteOwner != 0L && activeWriteOwner != attemptId) {
            return false;
        }
        activeWriteOwner = attemptId;
        return true;
    }

    private static void reconcileRollbacks(List<Runnable> rollbacks) {
        if (rollbacks == null) {
            return;
        }
        List<Runnable> snapshot;
        synchronized (rollbacks) {
            if (rollbacks.isEmpty()) {
                return;
            }
            snapshot = new ArrayList<>(rollbacks);
            rollbacks.clear();
        }
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            Runnable action = snapshot.get(i);
            if (action == null) {
                continue;
            }
            try {
                action.run();
            } catch (Exception e) {
                Constants.LOG.error("Failed to reconcile applied write during cancellation: {}", e.getMessage());
            }
        }
    }

    public static void runTunnelBackground() {
        final long expectedGeneration;
        final ManagedTunnel targetTunnel;
        final Config targetConfig;
        final TunnelInfo targetInfo;
        synchronized (LIFECYCLE_LOCK) {
            expectedGeneration = lifecycleGeneration.get();
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
            if (lifecycleGeneration.get() != expectedGeneration
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

    private static boolean isStartOwnerLocked(
        long expectedGeneration,
        CloudFlaredHandler targetHandler,
        ManagedTunnel targetTunnel,
        Config targetConfig
    ) {
        return lifecycleGeneration.get() == expectedGeneration
            && handler == targetHandler
            && activeTunnel == targetTunnel
            && configManager != null
            && configManager.CONFIG == targetConfig;
    }

    private enum PreparationOutcome {
        PREPARED,
        BUSY_WRITE_OWNER,
        CANCELED,
        FAILED
    }

    private static boolean initiateTunnel(final long expectedGeneration) {
        return initiateTunnelWithOutcome(expectedGeneration) == PreparationOutcome.PREPARED;
    }

    private static PreparationOutcome initiateTunnelWithOutcome(final long expectedGeneration) {
        final CloudFlaredHandler targetHandler;
        final ManagedTunnel targetTunnel;
        final Config targetConfig;
        final Object prepAttemptToken = new Object();

        synchronized (LIFECYCLE_LOCK) {
            if (lifecycleGeneration.get() != expectedGeneration
                || handler == null
                || activeTunnel == null
                || configManager == null
                || configManager.CONFIG == null) {
                return (lifecycleGeneration.get() != expectedGeneration)
                    ? PreparationOutcome.CANCELED
                    : PreparationOutcome.FAILED;
            }
            if (activeTunnel.requiresCliAuth() && !handler.isAuthenticated()) {
                return PreparationOutcome.FAILED;
            }
            targetHandler = handler;
            targetTunnel = activeTunnel;
            targetConfig = configManager.CONFIG;
            IN_FLIGHT_PREPARATIONS.put(prepAttemptToken, targetTunnel);
        }

        final long startWriteAttemptId = writeAttemptSeq.incrementAndGet();
        final AtomicBoolean rejectedByBusyWriter = new AtomicBoolean(false);
        final List<Runnable> appliedWriteRollbacks = Collections.synchronizedList(new ArrayList<>());

        // Slow discovery stays outside LIFECYCLE_LOCK and WRITE_OWNER_LOCK; ownership is verified
        // under WRITE_OWNER_LOCK BEFORE and AFTER each external write (tunnel create / DNS / YAML / remote ingress).
        ManagedTunnel.WriteCommitCoordinator writeCoordinator = new ManagedTunnel.WriteCommitCoordinator() {
            @Override
            public boolean executeIfOwner(BooleanSupplier writeAction) {
                synchronized (WRITE_OWNER_LOCK) {
                    if (activeWriteOwner != 0L && activeWriteOwner != startWriteAttemptId) {
                        Constants.LOG.debug("Aborting tunnel external write while another write sequence is active.");
                        rejectedByBusyWriter.set(true);
                        return false;
                    }
                    synchronized (LIFECYCLE_LOCK) {
                        if (!isStartOwnerLocked(expectedGeneration, targetHandler, targetTunnel, targetConfig)) {
                            Constants.LOG.debug("Aborting stale tunnel external write for generation {}.", expectedGeneration);
                            return false;
                        }
                    }
                    activeWriteOwner = startWriteAttemptId;
                    if (!writeAction.getAsBoolean()) {
                        return false;
                    }
                    synchronized (LIFECYCLE_LOCK) {
                        return isStartOwnerLocked(expectedGeneration, targetHandler, targetTunnel, targetConfig);
                    }
                }
            }

            @Override
            public boolean isStillOwner() {
                synchronized (LIFECYCLE_LOCK) {
                    return isStartOwnerLocked(expectedGeneration, targetHandler, targetTunnel, targetConfig);
                }
            }

            @Override
            public void onAppliedWrite(Runnable rollbackAction) {
                if (rollbackAction != null) {
                    appliedWriteRollbacks.add(rollbackAction);
                }
            }
        };

        try {
            if (lifecycleGeneration.get() != expectedGeneration) {
                return PreparationOutcome.CANCELED;
            }
            TunnelInfo prepared = targetTunnel.prepareTunnel(
                targetConfig,
                ModPlatformInstance.xplat().getLocalAddress(),
                ModPlatformInstance.xplat().getServerPort(),
                writeCoordinator
            );

            if (prepared == null) {
                synchronized (LIFECYCLE_LOCK) {
                    if (lifecycleGeneration.get() == expectedGeneration && activeTunnel == targetTunnel) {
                        tunnelPrepared = false;
                        preparedGeneration = -1L;
                        preparedTunnel = null;
                        preparedConfig = null;
                    }
                }
                if (rejectedByBusyWriter.get()) {
                    return PreparationOutcome.BUSY_WRITE_OWNER;
                }
                return (lifecycleGeneration.get() != expectedGeneration)
                    ? PreparationOutcome.CANCELED
                    : PreparationOutcome.FAILED;
            }

            boolean published = false;
            synchronized (LIFECYCLE_LOCK) {
                if (!isStartOwnerLocked(expectedGeneration, targetHandler, targetTunnel, targetConfig)) {
                    Constants.LOG.debug("Discarding stale tunnel preparation result for generation {}.", expectedGeneration);
                } else {
                    info = prepared;
                    tunnelPrepared = true;
                    preparedGeneration = expectedGeneration;
                    preparedTunnel = targetTunnel;
                    preparedConfig = targetConfig;
                    updateConfigWithTunnelInfo(targetConfig, prepared);
                    published = true;
                }
            }
            if (!published) {
                reconcileRollbacks(appliedWriteRollbacks);
                return PreparationOutcome.CANCELED;
            }
            return PreparationOutcome.PREPARED;
        } finally {
            IN_FLIGHT_PREPARATIONS.remove(prepAttemptToken);
            synchronized (WRITE_OWNER_LOCK) {
                if (activeWriteOwner == startWriteAttemptId) {
                    activeWriteOwner = 0L;
                }
            }
        }
    }

    private static void updateConfigWithTunnelInfo(Config targetConfig, TunnelInfo tunnel) {
        if (tunnel == null || targetConfig == null || configManager == null || configManager.CONFIG != targetConfig) {
            return;
        }
        configManager.updateTunnelIdentityIfUnchanged(targetConfig, tunnel.getId(), tunnel.getName());
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
            final long startGeneration;
            final TunnelMode mode;
            final ManagedTunnel targetTunnel;
            final Object startAttemptOwner = new Object();

            synchronized (LIFECYCLE_LOCK) {
                if (activeTunnel != null && activeTunnel.isTunnelRunning()) {
                    long pid = activeTunnel.getActivePid();
                    sender.sendMessage("\u00A7e[ServerFlared] Tunnel is already running" + (pid > 0 ? " (PID: " + pid + ")" : "") + ".");
                    return;
                }
                Config currentConfig = (configManager != null) ? configManager.CONFIG : null;
                mode = getConfiguredMode();
                if (ConfigManager.isPendingFirstTimeSetup(currentConfig)) {
                    sender.sendMessage(
                        "\u00A7e[ServerFlared] Start aborted: required settings are incomplete for "
                            + mode.name() + " mode. Update config.yml, then run /serverflared reload."
                    );
                    return;
                }
                if (handler == null) {
                    handler = CloudFlaredHandler.createInstance();
                }
                if (handler == null) {
                    sender.sendMessage("\u00A7c[ServerFlared] Failed to initialize cloudflared binary handler.");
                    return;
                }
                if (activeTunnel == null || activeTunnel.getMode() != mode) {
                    activeTunnel = createTunnelHandler(mode, handler);
                }
                if (activeTunnel.requiresCliAuth() && !handler.isAuthenticated()) {
                    sender.sendMessage("\u00A7c[ServerFlared] Cloudflared is not authenticated. Run /serverflared login first.");
                    return;
                }
                startGeneration = lifecycleGeneration.get();
                targetTunnel = activeTunnel;
                sender.sendMessage("\u00A7e[ServerFlared] Starting cloudflared tunnel (" + mode.name() + ")...");
                targetTunnel.setState(TunnelState.STARTING);
                targetTunnel.setLastConnectionDetail("Initiating tunnel configuration...");
                startingStateOwner = startAttemptOwner;
            }

            PreparationOutcome outcome = initiateTunnelWithOutcome(startGeneration);
            if (outcome != PreparationOutcome.PREPARED) {
                synchronized (LIFECYCLE_LOCK) {
                    if (startingStateOwner == startAttemptOwner) {
                        startingStateOwner = null;
                        if (activeTunnel == targetTunnel
                            && !targetTunnel.isTunnelRunning()
                            && targetTunnel.getState() == TunnelState.STARTING) {
                            if (outcome == PreparationOutcome.BUSY_WRITE_OWNER || outcome == PreparationOutcome.CANCELED) {
                                targetTunnel.setState(TunnelState.STOPPED);
                                targetTunnel.setLastConnectionDetail("Stopped");
                            } else {
                                targetTunnel.setState(TunnelState.ERROR);
                            }
                        }
                    }
                }
                if (outcome == PreparationOutcome.BUSY_WRITE_OWNER) {
                    sender.sendMessage(
                        "\u00A7e[ServerFlared] Another tunnel reload or start is already in progress. Please retry /serverflared start after it finishes."
                    );
                } else if (outcome == PreparationOutcome.CANCELED || lifecycleGeneration.get() != startGeneration) {
                    sender.sendMessage("\u00A7e[ServerFlared] Tunnel start was canceled by a concurrent stop or reload.");
                } else {
                    sender.sendMessage("\u00A7c[ServerFlared] Failed to prepare tunnel: " + targetTunnel.getLastConnectionDetail());
                }
                return;
            }

            boolean started;
            String hostName;
            synchronized (LIFECYCLE_LOCK) {
                if (startingStateOwner == startAttemptOwner) {
                    startingStateOwner = null;
                }
                if (lifecycleGeneration.get() != startGeneration
                    || activeTunnel != targetTunnel
                    || configManager == null
                    || configManager.CONFIG == null) {
                    sender.sendMessage("\u00A7e[ServerFlared] Tunnel start was canceled by a concurrent stop or reload.");
                    return;
                }
                started = activeTunnel.startTunnel(configManager.CONFIG, info);
                hostName = configManager.CONFIG.getHostName();
                if (started) {
                    launchedGeneration = startGeneration;
                    modDisabled = false;
                    firstTimeMode = false;
                }
            }

            if (started) {
                sender.sendMessage("\u00A7a[ServerFlared] Tunnel started successfully (" + mode.name() + ") for \u00A7f" + hostName);
            } else {
                sender.sendMessage("\u00A7c[ServerFlared] Failed to start cloudflared tunnel. Check server logs for details.");
            }
        }, ASYNC_EXECUTOR);
    }

    public static CompletableFuture<Void> stopTunnelAsync(final CommandSenderAdapter sender) {
        return CompletableFuture.runAsync(() -> {
            boolean wasRunning;
            ManagedTunnel toTerminate;
            synchronized (LIFECYCLE_LOCK) {
                // Always invalidate pending/in-flight launches BEFORE early-returning on no active process.
                lifecycleGeneration.incrementAndGet();
                pendingTunnelStart = false;
                tunnelPrepared = false;
                preparedGeneration = -1L;
                launchedGeneration = -1L;
                preparedTunnel = null;
                preparedConfig = null;
                tunnelInitStarted.set(false);
                activeLoginAttempt.set(null);
                startingStateOwner = null;
                if (handler != null) {
                    handler.cancelAuthentication();
                }
                terminateInFlightPreparations();

                wasRunning = activeTunnel != null && activeTunnel.isTunnelRunning();
                toTerminate = activeTunnel;
                if (!wasRunning) {
                    if (activeTunnel != null) {
                        activeTunnel.setState(TunnelState.STOPPED);
                    }
                    sender.sendMessage("\u00A7e[ServerFlared] Tunnel is already stopped.");
                    return;
                }
                sender.sendMessage("\u00A7e[ServerFlared] Stopping cloudflared tunnel...");
                toTerminate.terminateProcesses();
                sender.sendMessage("\u00A7a[ServerFlared] Cloudflared tunnel stopped.");
            }
        }, ASYNC_EXECUTOR);
    }

    public static CompletableFuture<Void> reloadAsync(final CommandSenderAdapter sender) {
        return CompletableFuture.runAsync(() -> {
            sender.sendMessage("\u00A7e[ServerFlared] Reloading config.yml and validating tunnel configuration...");

            final Config candidateConfig;
            final TunnelMode candidateMode;
            final CloudFlaredHandler candidateHandler;
            final ManagedTunnel candidateTunnel;
            final long reloadBaseGeneration;
            final Object prepAttemptToken = new Object();
            final long reloadAttemptId = writeAttemptSeq.incrementAndGet();

            // Validate candidate configuration and prerequisites under LIFECYCLE_LOCK BEFORE stopping active tunnel.
            synchronized (LIFECYCLE_LOCK) {
                if (configManager == null) {
                    configManager = new ConfigManager();
                }
                candidateConfig = configManager.loadCandidateConfig();
                if (candidateConfig == null) {
                    sender.sendMessage("\u00A7c[ServerFlared] Failed to reload config.yml. Active tunnel left unchanged.");
                    return;
                }

                candidateMode = candidateConfig.getResolvedTunnelMode();
                candidateHandler = (handler != null) ? handler : CloudFlaredHandler.createInstance();
                if (candidateHandler == null) {
                    sender.sendMessage("\u00A7c[ServerFlared] Failed to initialize cloudflared binary handler. Active tunnel left unchanged.");
                    return;
                }

                candidateTunnel = createReloadCandidateTunnelHandler(candidateMode, candidateHandler, reloadAttemptId);
                if (candidateTunnel.requiresCliAuth() && !candidateHandler.isAuthenticated()) {
                    if (getConfiguredMode().isRemote()) {
                        sender.sendMessage(
                            "\u00A7e[ServerFlared] Reload aborted: cloudflared is not authenticated for LOCAL mode. "
                                + "Run 'cloudflared tunnel login' as the server user to generate cert.pem, then retry /serverflared reload. Active tunnel left unchanged."
                        );
                    } else {
                        sender.sendMessage(
                            "\u00A7e[ServerFlared] Reload aborted: cloudflared is not authenticated for LOCAL mode. "
                                + "Run /serverflared login first. Active tunnel left unchanged."
                        );
                    }
                    return;
                }
                if (ConfigManager.isPendingFirstTimeSetup(candidateConfig)) {
                    sender.sendMessage(
                        "\u00A7e[ServerFlared] Reload aborted: required settings are incomplete for "
                            + candidateMode.name() + " mode. Update config.yml, then run /serverflared reload. Active tunnel left unchanged."
                    );
                    return;
                }

                reloadBaseGeneration = lifecycleGeneration.get();
                candidateTunnel.setState(TunnelState.STARTING);
                candidateTunnel.setLastConnectionDetail("Preparing reloaded tunnel configuration...");
                IN_FLIGHT_PREPARATIONS.put(prepAttemptToken, candidateTunnel);
            }

            // Keep candidateTunnel private during preparation; verify ownership before and after each external write,
            // and publish config, tunnel, info, and prepared state atomically only after prepareTunnel completes.
            final AtomicBoolean canceledByOwnership = new AtomicBoolean(false);
            final List<Runnable> appliedWriteRollbacks = Collections.synchronizedList(new ArrayList<>());
            boolean reloadCommitted = false;

            ManagedTunnel.WriteCommitCoordinator reloadCoordinator = new ManagedTunnel.WriteCommitCoordinator() {
                @Override
                public boolean executeIfOwner(BooleanSupplier writeAction) {
                    synchronized (WRITE_OWNER_LOCK) {
                        if (!claimOrVerifyWriteOwner(reloadAttemptId, reloadBaseGeneration)) {
                            canceledByOwnership.set(true);
                            return false;
                        }
                        if (!writeAction.getAsBoolean()) {
                            return false;
                        }
                        if (lifecycleGeneration.get() != reloadBaseGeneration) {
                            canceledByOwnership.set(true);
                            return false;
                        }
                        return true;
                    }
                }

                @Override
                public boolean isStillOwner() {
                    return lifecycleGeneration.get() == reloadBaseGeneration;
                }

                @Override
                public void onAppliedWrite(Runnable rollbackAction) {
                    if (rollbackAction != null) {
                        appliedWriteRollbacks.add(rollbackAction);
                    }
                }
            };

            try {
                if (lifecycleGeneration.get() != reloadBaseGeneration) {
                    sender.sendMessage("\u00A7e[ServerFlared] Reload canceled by a concurrent stop or shutdown.");
                    return;
                }

                TunnelInfo candidateInfo = candidateTunnel.prepareTunnel(
                    candidateConfig,
                    ModPlatformInstance.xplat().getLocalAddress(),
                    ModPlatformInstance.xplat().getServerPort(),
                    reloadCoordinator
                );

                if (candidateInfo == null) {
                    if (canceledByOwnership.get() || lifecycleGeneration.get() != reloadBaseGeneration) {
                        sender.sendMessage("\u00A7e[ServerFlared] Reload canceled by a concurrent reload, stop, or shutdown.");
                    } else {
                        sender.sendMessage(
                            "\u00A7c[ServerFlared] Reload aborted: failed to prepare tunnel ("
                                + candidateTunnel.getLastConnectionDetail() + "). Active tunnel left unchanged."
                        );
                    }
                    return;
                }

                if (lifecycleGeneration.get() != reloadBaseGeneration) {
                    reconcileRollbacks(appliedWriteRollbacks);
                    sender.sendMessage("\u00A7e[ServerFlared] Reload canceled by a concurrent stop or shutdown.");
                    return;
                }

                sender.sendMessage("\u00A77[ServerFlared] Config validated (tunnelMode=" + candidateMode.name()
                    + ", commandPermissionMode=" + candidateConfig.getCommandPermissionMode()
                    + "). Starting reloaded tunnel...");

                boolean canceledBeforePublish = false;
                boolean launchFailed = false;

                // Verify write ownership before launching the candidate, then launch and wait for an edge
                // connection OUTSIDE LIFECYCLE_LOCK so stop/cleanup can cancel promptly and the working
                // connector stays active until the replacement confirms edge connectivity.
                synchronized (WRITE_OWNER_LOCK) {
                    if (!claimOrVerifyWriteOwner(reloadAttemptId, reloadBaseGeneration)) {
                        canceledBeforePublish = true;
                    }
                }

                if (!canceledBeforePublish) {
                    boolean started = candidateTunnel.startTunnel(candidateConfig, candidateInfo);
                    boolean edgeReady = started && candidateTunnel.awaitEdgeConnection(
                        RELOAD_EDGE_CONNECT_TIMEOUT_MS,
                        () -> lifecycleGeneration.get() == reloadBaseGeneration
                    );
                    if (lifecycleGeneration.get() != reloadBaseGeneration) {
                        candidateTunnel.terminateProcesses();
                        canceledBeforePublish = true;
                    } else if (!started || !edgeReady) {
                        candidateTunnel.terminateProcesses();
                        launchFailed = true;
                    } else {
                        synchronized (WRITE_OWNER_LOCK) {
                            synchronized (LIFECYCLE_LOCK) {
                                if (!claimOrVerifyWriteOwner(reloadAttemptId, reloadBaseGeneration)) {
                                    candidateTunnel.terminateProcesses();
                                    canceledBeforePublish = true;
                                } else if (!candidateTunnel.isTunnelRunning()) {
                                    candidateTunnel.terminateProcesses();
                                    launchFailed = true;
                                } else {
                                    long newGen = lifecycleGeneration.incrementAndGet();
                                    ManagedTunnel previousTunnel = activeTunnel;
                                    handler = candidateHandler;
                                    configManager.applyConfig(candidateConfig);
                                    activeTunnel = candidateTunnel;
                                    info = candidateInfo;
                                    tunnelPrepared = true;
                                    preparedGeneration = newGen;
                                    preparedTunnel = candidateTunnel;
                                    preparedConfig = candidateConfig;
                                    launchedGeneration = newGen;
                                    modDisabled = false;
                                    firstTimeMode = false;
                                    updateConfigWithTunnelInfo(candidateConfig, candidateInfo);
                                    notifyCommandTreeSync();
                                    if (candidateTunnel instanceof LocalManagedTunnel) {
                                        ((LocalManagedTunnel) candidateTunnel).syncStagedConfigToCanonical();
                                    }
                                    reloadCommitted = true;
                                    if (previousTunnel != null && previousTunnel != candidateTunnel) {
                                        previousTunnel.terminateProcesses();
                                        if (previousTunnel instanceof LocalManagedTunnel) {
                                            ((LocalManagedTunnel) previousTunnel).deleteStagedConfigFile();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (canceledBeforePublish) {
                    reconcileRollbacks(appliedWriteRollbacks);
                    sender.sendMessage("\u00A7e[ServerFlared] Reload canceled by a concurrent reload, stop, or shutdown.");
                    return;
                }
                if (launchFailed) {
                    reconcileRollbacks(appliedWriteRollbacks);
                    sender.sendMessage(
                        "\u00A7c[ServerFlared] Reload aborted: failed to start cloudflared tunnel. Active tunnel left unchanged."
                    );
                    return;
                }

                sender.sendMessage("\u00A7a[ServerFlared] Reload complete! Tunnel running (" + candidateMode.name()
                    + ") for \u00A7f" + candidateConfig.getHostName());
            } finally {
                if (!reloadCommitted && candidateTunnel instanceof LocalManagedTunnel) {
                    ((LocalManagedTunnel) candidateTunnel).deleteStagedConfigFile();
                }
                IN_FLIGHT_PREPARATIONS.remove(prepAttemptToken);
                synchronized (WRITE_OWNER_LOCK) {
                    if (activeWriteOwner == reloadAttemptId) {
                        activeWriteOwner = 0L;
                    }
                }
            }
        }, ASYNC_EXECUTOR);
    }

    public static CompletableFuture<Void> loginAsync(final CommandSenderAdapter sender) {
        final long requestedGeneration = lifecycleGeneration.get();
        return CompletableFuture.runAsync(() -> {
            final long authGeneration;
            final Object loginToken = new Object();
            final CompletableFuture<Boolean> authFuture;

            synchronized (LIFECYCLE_LOCK) {
                if (lifecycleGeneration.get() != requestedGeneration) {
                    sender.sendMessage("\u00A7e[ServerFlared] Cloudflared login was canceled by a concurrent stop or shutdown.");
                    return;
                }
                TunnelMode mode = getConfiguredMode();
                if (mode.isRemote()) {
                    sender.sendMessage(
                        "\u00A7e[ServerFlared] CLI login (cert.pem) is not required in " + mode.name()
                            + " mode. If you are switching to LOCAL mode, run 'cloudflared tunnel login' as the server user to generate cert.pem, then retry /serverflared reload."
                    );
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
                if (!activeLoginAttempt.compareAndSet(null, loginToken)) {
                    sender.sendMessage("\u00A7e[ServerFlared] Cloudflared login is already in progress. Check console/chat for the login link.");
                    return;
                }
                authGeneration = requestedGeneration;
                if (lifecycleGeneration.get() != authGeneration) {
                    activeLoginAttempt.compareAndSet(loginToken, null);
                    sender.sendMessage("\u00A7e[ServerFlared] Cloudflared login was canceled by a concurrent stop or shutdown.");
                    return;
                }
                sender.sendMessage("\u00A7e[ServerFlared] Launching 'cloudflared tunnel login'... Waiting for authorization URL...");
                try {
                    authFuture = handler.authenticate(url -> {
                        if (!sender.isConsole()) {
                            sender.sendClickableUrl(
                                "\u00A7a[ServerFlared] \u00A7eClick here to authenticate Cloudflared: \u00A7b\u00A7n" + url,
                                url
                            );
                        } else {
                            sender.sendMessage("[ServerFlared] Authenticate Cloudflared at: " + url);
                        }
                    }, () -> lifecycleGeneration.get() == authGeneration);
                } catch (Throwable t) {
                    activeLoginAttempt.compareAndSet(loginToken, null);
                    throw t;
                }
            }

            authFuture.whenComplete((authenticated, throwable) -> {
                activeLoginAttempt.compareAndSet(loginToken, null);
                if (lifecycleGeneration.get() != authGeneration) {
                    sender.sendMessage("\u00A7e[ServerFlared] Cloudflared login was canceled by a concurrent stop or shutdown.");
                } else if (throwable != null || !Boolean.TRUE.equals(authenticated)) {
                    sender.sendMessage("\u00A7c[ServerFlared] Cloudflared authentication failed or timed out.");
                } else {
                    sender.sendMessage("\u00A7a[ServerFlared] Cloudflared authentication completed successfully!");
                    resumeAfterAuthentication(authGeneration);
                }
            });
        }, ASYNC_EXECUTOR);
    }
}
