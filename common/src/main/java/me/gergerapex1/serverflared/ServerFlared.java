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
import me.gergerapex1.serverflared.cloudflared.handler.TunnelInfo;
import me.gergerapex1.serverflared.command.CommandSenderAdapter;
import me.gergerapex1.serverflared.utils.config.ConfigManager;

// This class is part of the common project meaning it is shared between all supported loaders. Code written here can only
// import and access the vanilla codebase, libraries used by vanilla, and optionally third party libraries that provide
// common compatible binaries. This means common code can not directly use loader specific concepts such as Forge events
// however it will be compatible with all supported mod loaders.
public class ServerFlared {
    private static ConfigManager configManager;
    private static CloudFlaredHandler handler;
    private static LocalManagedTunnel localHandler;
    private static volatile boolean modDisabled = false;
	private static final AtomicBoolean tunnelInitStarted = new AtomicBoolean(false);
	private static final AtomicBoolean loginInProgress = new AtomicBoolean(false);
	private static final Object LIFECYCLE_LOCK = new Object();
	private static volatile boolean pendingTunnelStart = false;
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
        configManager = new ConfigManager();
        handler = CloudFlaredHandler.createInstance();
        if (handler != null) {
            localHandler = new LocalManagedTunnel(handler);
        }
        if (configManager.firstTime) {
			firstTimeMode = true;
			modDisabled = true;
            logFirstTimeSetup();
			//firstTimeSetupAuthentication();
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
			handler.authenticate().thenAccept(v -> {
				if(v) {
					modDisabled = false;
					Constants.LOG.info("Authentication completed, resuming mod operation.");
					if(pendingTunnelStart) {
						handleTunnel();
					}
				} else {
					Constants.LOG.info("Authentication process went wrong");
				}
            });
        }
    }

    private static void logFirstTimeSetup() {
        Path configPath = ModPlatformInstance.xplat().getConfigDirectory()
            .resolve(Constants.CONFIG_DIR)
            .resolve("config.yml");

        Constants.LOG.info("First time setup detected, please configure the TUNNEL NAME (\"tunnelName\") "
            + "and SUBDOMAIN (\"subdomain\") in the config file generated at {}", configPath.toString());
        Constants.LOG.info("If you have existing cloudflared tunnel or remotely managed tunnel, please configure the TUNNEL ID "
            + "(\"tunnelID\") instead");
        Constants.LOG.info("After configuring the tunnel name, please restart the server.\n");
		if(handler != null && !handler.isAuthenticated()) {
			Constants.LOG.info("");
			Constants.LOG.info("You also need to authenticate to cloudflared. A link will be displayed shortly.");
			Constants.LOG.info("If you are running this in your host machine, a authentication link will be open automatically");
		}
        Constants.LOG.info("Mod disabled.");
    }
	// TODO: reusable code
	private static void firstTimeSetupAuthentication() {
		handler.authenticate().thenAccept(v -> {
			if(v) {
				Constants.LOG.info("Authentication completed, please configure the config file and restart the server");
			} else {
				Constants.LOG.info("Authentication process went wrong");
			}
		});
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

		Constants.LOG.debug("Handling tunnel now...");
		initiateTunnel();
	}
    public static void startedServer() {
        if(modDisabled) {
            return;
        }
        Constants.LOG.debug("Starting tunnel in background...");
        localHandler.createTunnelConfig(info.getId(), ModPlatformInstance.xplat().getLocalAddress(), configManager.CONFIG.getHostName(), ModPlatformInstance.xplat().getServerPort());
        CompletableFuture.runAsync(ServerFlared::runTunnelBackground, ASYNC_EXECUTOR);
        Constants.LOG.info("Tunnel started! You can join the server with " + configManager.CONFIG.getHostName());
    }
    public static void cleanup() {
		Constants.LOG.info("Stopping serverflared");
        Constants.LOG.info("Stopping all processes...");
		if (handler != null) {
			handler.terminateProcesses();
		}
        if (localHandler != null) {
            localHandler.terminateProcesses();
        }
		Constants.LOG.info("Successfully stopped serverflared. See you next time!");
    }
    public static void runTunnelBackground() {
        String target = (info != null && info.getId() != null && !info.getId().isEmpty())
            ? info.getId()
            : (configManager != null && configManager.CONFIG != null ? configManager.CONFIG.getTunnelName() : "");
        localHandler.runLocalTunnel(target);
    }
    private static void initiateTunnel() {
		if(handler == null || !handler.isAuthenticated()) return;
        // Check if tunnel exist with the config
        TunnelInfo initialTunnelInfo = new TunnelInfo();
        initialTunnelInfo.setName(configManager.CONFIG.getTunnelName());
        initialTunnelInfo.setId(configManager.CONFIG.getTunnelId());
        boolean isTunnelExist = handler.validateTunnelExist(initialTunnelInfo);
        Constants.LOG.debug("Is tunnel exist: {}", isTunnelExist);
        if(isTunnelExist) {
            Constants.LOG.debug("Tunnel with name {} and ID {} not found, creating new tunnel", initialTunnelInfo.getName(), initialTunnelInfo.getId());

            TunnelInfo createdTunnel = localHandler.createTunnel(initialTunnelInfo);
            if (createdTunnel == null) {
                Constants.LOG.error("Failed to create tunnel for name {} and ID {}. Aborting tunnel initiation.", initialTunnelInfo.getName(), initialTunnelInfo.getId());
                return;
            }
            localHandler.routeDnsToTunnel(createdTunnel.getId(), configManager.CONFIG.getHostName());

            updateConfigWithTunnelInfo(createdTunnel);

            initialTunnelInfo = createdTunnel;
            Constants.LOG.info("New tunnel created with ID {}", createdTunnel.getId());
        } else {
            TunnelInfo existingTunnel = handler.getTunnelInfo(initialTunnelInfo);
            if (existingTunnel != null) {
                Constants.LOG.info("Tunnel with name {} and ID {} found", existingTunnel.getName(), existingTunnel.getId());
                initialTunnelInfo = existingTunnel;
            }
        }

        info = initialTunnelInfo;
    }
    private static void updateConfigWithTunnelInfo(TunnelInfo tunnel) {
        configManager.CONFIG.setTunnelId(tunnel.getId());
        configManager.CONFIG.setTunnelName(tunnel.getName());
        configManager.saveConfig();
    }

	public static ConfigManager getConfigManager() {
		return configManager;
	}

	public static CloudFlaredHandler getHandler() {
		return handler;
	}

	public static LocalManagedTunnel getLocalHandler() {
		return localHandler;
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
				if (localHandler != null && localHandler.isTunnelRunning()) {
					long pid = localHandler.getActivePid();
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
				if (localHandler == null) {
					localHandler = new LocalManagedTunnel(handler);
				}
				if (!handler.isAuthenticated()) {
					sender.sendMessage("\u00A7c[ServerFlared] Cloudflared is not authenticated. Run /serverflared login first.");
					return;
				}
				sender.sendMessage("\u00A7e[ServerFlared] Starting cloudflared tunnel...");
				localHandler.setState(LocalManagedTunnel.TunnelState.STARTING);
				localHandler.setLastConnectionDetail("Initiating tunnel configuration...");
				if (info == null || info.getId() == null || info.getId().isEmpty() || handler.isDefaultTunnelUUID(info.getId())) {
					initiateTunnel();
				}
				String tunnelId = (info != null && info.getId() != null && !info.getId().isEmpty())
					? info.getId()
					: configManager.CONFIG.getTunnelId();
				localHandler.createTunnelConfig(
					tunnelId,
					ModPlatformInstance.xplat().getLocalAddress(),
						configManager.CONFIG.getHostName(),
					ModPlatformInstance.xplat().getServerPort()
				);
				String runTarget = (tunnelId != null && !tunnelId.isEmpty() && !handler.isDefaultTunnelUUID(tunnelId))
					? tunnelId
					: configManager.CONFIG.getTunnelName();
				boolean started = localHandler.runLocalTunnel(runTarget);
				if (started) {
					modDisabled = false;
					firstTimeMode = false;
					sender.sendMessage("\u00A7a[ServerFlared] Tunnel started successfully for \u00A7f" + configManager.CONFIG.getHostName());
				} else {
					sender.sendMessage("\u00A7c[ServerFlared] Failed to start cloudflared tunnel. Check server logs for details.");
				}
			}
		}, ASYNC_EXECUTOR);
	}

	public static CompletableFuture<Void> stopTunnelAsync(final CommandSenderAdapter sender) {
		return CompletableFuture.runAsync(() -> {
			synchronized (LIFECYCLE_LOCK) {
				if (localHandler == null || !localHandler.isTunnelRunning()) {
					if (localHandler != null) {
						localHandler.setState(LocalManagedTunnel.TunnelState.STOPPED);
					}
					sender.sendMessage("\u00A7e[ServerFlared] Tunnel is already stopped.");
					return;
				}
				sender.sendMessage("\u00A7e[ServerFlared] Stopping cloudflared tunnel...");
				localHandler.terminateProcesses();
				sender.sendMessage("\u00A7a[ServerFlared] Cloudflared tunnel stopped.");
			}
		}, ASYNC_EXECUTOR);
	}

	public static CompletableFuture<Void> reloadAsync(final CommandSenderAdapter sender) {
		return CompletableFuture.runAsync(() -> {
			synchronized (LIFECYCLE_LOCK) {
				sender.sendMessage("\u00A7e[ServerFlared] Reloading config.yml and restarting tunnel...");
				if (configManager == null) {
					configManager = new ConfigManager();
				}
				boolean reloaded = configManager.reload();
				if (!reloaded) {
					sender.sendMessage("\u00A7c[ServerFlared] Failed to reload config.yml. Please check the file syntax.");
					return;
				}
				notifyCommandTreeSync();
				sender.sendMessage("\u00A77[ServerFlared] Config reloaded (commandPermissionMode="
					+ configManager.CONFIG.getCommandPermissionMode() + "). Stopping active tunnel...");

				if (localHandler != null) {
					localHandler.terminateProcesses();
				}
				if (handler == null) {
					handler = CloudFlaredHandler.createInstance();
				}
				if (localHandler == null && handler != null) {
					localHandler = new LocalManagedTunnel(handler);
				}
				if (handler == null || !handler.isAuthenticated()) {
					sender.sendMessage("\u00A7e[ServerFlared] Config reloaded, but cloudflared is not authenticated. Run /serverflared login to authenticate.");
					return;
				}
				localHandler.setState(LocalManagedTunnel.TunnelState.STARTING);
				localHandler.setLastConnectionDetail("Re-initializing tunnel after reload...");
				initiateTunnel();
				String tunnelId = (info != null && info.getId() != null && !info.getId().isEmpty())
					? info.getId()
					: configManager.CONFIG.getTunnelId();
				localHandler.createTunnelConfig(
					tunnelId,
					ModPlatformInstance.xplat().getLocalAddress(),
					configManager.CONFIG.getHostName(),
					ModPlatformInstance.xplat().getServerPort()
				);
				String runTarget = (tunnelId != null && !tunnelId.isEmpty() && !handler.isDefaultTunnelUUID(tunnelId))
					? tunnelId
					: configManager.CONFIG.getTunnelName();
				boolean started = localHandler.runLocalTunnel(runTarget);
				if (started) {
					modDisabled = false;
					firstTimeMode = false;
					sender.sendMessage("\u00A7a[ServerFlared] Reload complete! Tunnel running for \u00A7f" + configManager.CONFIG.getHostName());
				} else {
					sender.sendMessage("\u00A7c[ServerFlared] Config reloaded, but failed to start cloudflared tunnel.");
				}
			}
		}, ASYNC_EXECUTOR);
	}

	public static CompletableFuture<Void> loginAsync(final CommandSenderAdapter sender) {
		return CompletableFuture.runAsync(() -> {
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
					modDisabled = false;
					sender.sendMessage("\u00A7a[ServerFlared] Cloudflared authentication completed successfully!");
				}
			});
		}, ASYNC_EXECUTOR);
	}
}
