package me.gergerapex1.serverflared.cloudflared.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.ModPlatformInstance;
import me.gergerapex1.serverflared.process.ProcessHandler;
import me.gergerapex1.serverflared.process.SubCommand;
import me.gergerapex1.serverflared.utils.config.YamlHandler;

public class LocalManagedTunnel {
    public enum TunnelState {
        STOPPED,
        STARTING,
        RUNNING,
        ERROR
    }

    private final Pattern uuidRegex = Pattern.compile(
        "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("location=([A-Za-z0-9_-]+)");
    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("protocol=([A-Za-z0-9_-]+)");
    private static final Pattern CONN_INDEX_PATTERN = Pattern.compile("connIndex=(\\d+)");

    private final ProcessHandler executor;
    private final String tunnelConfigLocation;
    private final YamlHandler yamlHandler;
    CloudFlaredHandler cfHandler;

    private volatile TunnelState state = TunnelState.STOPPED;
    private volatile Process activeTunnelProcess;
    private volatile long startedAtMillis = 0L;
    private volatile String lastConnectionDetail = "Not connected";
    private volatile boolean stoppingIntentionally = false;

    public LocalManagedTunnel(CloudFlaredHandler cloudflaredHandler) {
        cfHandler = cloudflaredHandler;
        this.executor = new ProcessHandler(cfHandler != null ? cfHandler.getBinaryPath() : Constants.CLOUDFLARED_BINARY_NAME);
        Path cfgPath = getOrCreateTunnelConfig();
        this.tunnelConfigLocation = cfgPath != null ? cfgPath.toString() : "";
        this.yamlHandler = new YamlHandler();
    }

    public TunnelInfo createTunnel(TunnelInfo tunnelInfo) {
        try {
            AtomicReference<TunnelInfo> createdTunnelRef = new AtomicReference<>();
            Process process = executor.run(new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_CREATE, tunnelInfo.getName()), line -> {
                if (tunnelAlreadyExist(line)) {
                    Constants.LOG.debug("Cannot create tunnel. Tunnel with name {} already exists", tunnelInfo.getName());
                    createdTunnelRef.set(null);
                }
                Constants.LOG.debug(line);
                Matcher m = uuidRegex.matcher(line);
                if (m.find()) {
                    String group = m.group(1);
                    Constants.LOG.info("Created tunnel with ID: {}", group);

                    tunnelInfo.setId(group);
                }
                createdTunnelRef.set(tunnelInfo);
            }, Constants.LOG::error);
            if (process != null) {
                process.waitFor();
            }
            if (createdTunnelRef.get() == null) {
                Constants.LOG.info("Getting existing tunnel with name and ID: {}, {}", tunnelInfo.getName(), tunnelInfo.getId());
                TunnelInfo existingTunnel = cfHandler.getTunnelInfo(tunnelInfo);
                if (existingTunnel != null) {
                    Constants.LOG.info("Using existing tunnel, {}, {}", existingTunnel.getName(), existingTunnel.getId());
                    tunnelInfo.setTunnelAndId(existingTunnel);
                }
            } else {
                tunnelInfo.setId(createdTunnelRef.get().getId());
                tunnelInfo.setName(createdTunnelRef.get().getName());
            }
            return tunnelInfo;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Constants.LOG.error(e.getMessage());
            return null;
        }
    }

    public void routeDnsToTunnel(String tunnelName, String routeDnsDomain) {
        executor.run(new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_ROUTE, Constants.CMD_DNS, tunnelName, routeDnsDomain), null, null);
    }

    public synchronized boolean runLocalTunnel(String tunnelIdOrName) {
        if (isTunnelRunning()) {
            Constants.LOG.debug("Local tunnel process is already running.");
            return false;
        }
        stoppingIntentionally = false;
        state = TunnelState.STARTING;
        startedAtMillis = System.currentTimeMillis();
        lastConnectionDetail = "Starting cloudflared tunnel process...";
        try {
            Process process = executor.runAsync(
                new SubCommand(Constants.CMD_TUNNEL, "--config", "\"" + tunnelConfigLocation + "\"", Constants.CMD_RUN, tunnelIdOrName),
                this::handleTunnelLogLine,
                this::handleTunnelLogLine
            );
            this.activeTunnelProcess = process;
            if (process != null && process.isAlive()) {
                if (state == TunnelState.STARTING) {
                    state = TunnelState.RUNNING;
                    if ("Starting cloudflared tunnel process...".equals(lastConnectionDetail)) {
                        lastConnectionDetail = "Process running, awaiting edge connection...";
                    }
                }
                CompletableFuture.runAsync(() -> {
                    try {
                        int exitCode = process.waitFor();
                        if (stoppingIntentionally) {
                            state = TunnelState.STOPPED;
                            startedAtMillis = 0L;
                            lastConnectionDetail = "Stopped";
                        } else {
                            state = TunnelState.ERROR;
                            lastConnectionDetail = "Process exited with code " + exitCode;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                return true;
            } else {
                state = TunnelState.ERROR;
                lastConnectionDetail = "Process failed to stay alive";
                return false;
            }
        } catch (IOException e) {
            state = TunnelState.ERROR;
            lastConnectionDetail = "Failed to launch cloudflared: " + e.getMessage();
            Constants.LOG.error(e.getMessage());
            return false;
        }
    }

    private void handleTunnelLogLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }
        String trimmed = line.trim();
        String lower = trimmed.toLowerCase();
        if (lower.contains("registered tunnel connection") || lower.contains("location=")) {
            state = TunnelState.RUNNING;
            StringBuilder detail = new StringBuilder("Connected");
            Matcher locMatcher = LOCATION_PATTERN.matcher(trimmed);
            Matcher protoMatcher = PROTOCOL_PATTERN.matcher(trimmed);
            Matcher idxMatcher = CONN_INDEX_PATTERN.matcher(trimmed);
            if (locMatcher.find()) {
                detail.append(" [edge=").append(locMatcher.group(1));
                if (protoMatcher.find()) {
                    detail.append(", protocol=").append(protoMatcher.group(1));
                }
                if (idxMatcher.find()) {
                    detail.append(", connIndex=").append(idxMatcher.group(1));
                }
                detail.append("]");
            } else {
                detail.append(": ").append(trimmed);
            }
            lastConnectionDetail = detail.toString();
        } else if (lower.contains("err ") || lower.contains("error") || lower.contains("failed")) {
            lastConnectionDetail = trimmed;
        }
    }

    public void createTunnelConfig(String tunnelId, String localIp, String hostname, int serverPort) {
        String resolvedIp = (localIp == null || localIp.trim().isEmpty() || "0.0.0.0".equals(localIp.trim()))
            ? "localhost"
            : localIp.trim();
        int resolvedPort = serverPort > 0 ? serverPort : 25565;
		System.out.println("Creating tunnel config for tunnelId: " + tunnelId + ", localIp: " + resolvedIp + ", hostname: " + hostname + ", serverPort: " + resolvedPort);
        TunnelConfig config = new TunnelConfig(hostname,"tcp://" + resolvedIp + ":" + resolvedPort, tunnelId);
        Path configPath = getOrCreateTunnelConfig();
        if (configPath == null) {
            return;
        }
        try {
            yamlHandler.overwriteFileWithYaml(configPath.toString(), config);
        } catch (IOException e) {
            Constants.LOG.error(e.getMessage());
        }
    }

    public Path getOrCreateTunnelConfig() {
        Path configFilePath = Paths.get(
            ModPlatformInstance.xplat().getConfigDirectory().toAbsolutePath().toString(),
            Constants.CONFIG_DIR,
            Constants.TUNNEL_CONFIG_FILENAME
        );
        try {
            Path parent = configFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent); // make parent dirs if needed
            }
            if (Files.notExists(configFilePath)) {
                Files.createFile(configFilePath); // create file if it doesn't exist
            }
        } catch (IOException e) {
            Constants.LOG.error(e.getMessage());
            return null;
        }
        return configFilePath;
    }

    public synchronized void terminateProcesses() {
        stoppingIntentionally = true;
        executor.terminate();
        activeTunnelProcess = null;
        state = TunnelState.STOPPED;
        startedAtMillis = 0L;
        lastConnectionDetail = "Stopped";
    }

    public boolean isTunnelRunning() {
        Process proc = this.activeTunnelProcess;
        return proc != null && proc.isAlive();
    }

    public TunnelState getState() {
        if (state == TunnelState.RUNNING && !isTunnelRunning()) {
            return stoppingIntentionally ? TunnelState.STOPPED : TunnelState.ERROR;
        }
        return state;
    }

    public void setState(TunnelState state) {
        this.state = state;
    }

    public void setLastConnectionDetail(String detail) {
        this.lastConnectionDetail = detail;
    }

    public String getLastConnectionDetail() {
        return lastConnectionDetail;
    }

    public long getActivePid() {
        Process proc = this.activeTunnelProcess;
        if (proc != null && proc.isAlive()) {
            return ProcessHandler.getProcessPid(proc);
        }
        return -1L;
    }

    public long getUptimeMillis() {
        if (!isTunnelRunning() || startedAtMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - startedAtMillis);
    }

    public boolean tunnelAlreadyExist(String line) {
        return line.toLowerCase().contains("tunnel with name already exist");
    }
}
