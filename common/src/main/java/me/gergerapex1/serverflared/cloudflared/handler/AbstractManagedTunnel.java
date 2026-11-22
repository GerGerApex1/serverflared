package me.gergerapex1.serverflared.cloudflared.handler;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.process.ProcessHandler;
import me.gergerapex1.serverflared.process.SubCommand;

public abstract class AbstractManagedTunnel implements ManagedTunnel {
    private static final Pattern LOCATION_PATTERN = Pattern.compile("location=([A-Za-z0-9_-]+)");
    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("protocol=([A-Za-z0-9_-]+)");
    private static final Pattern CONN_INDEX_PATTERN = Pattern.compile("connIndex=(\\d+)");

    protected final ProcessHandler executor;
    protected final CloudFlaredHandler cfHandler;

    private final Object edgeConnectionLock = new Object();
    protected volatile TunnelState state = TunnelState.STOPPED;
    protected volatile Process activeTunnelProcess;
    protected volatile long startedAtMillis = 0L;
    protected volatile String lastConnectionDetail = "Not connected";
    protected volatile boolean stoppingIntentionally = false;
    protected volatile boolean edgeConnected = false;
    protected volatile boolean processExited = false;

    protected AbstractManagedTunnel(CloudFlaredHandler cloudflaredHandler) {
        this(cloudflaredHandler, new ProcessHandler(
            cloudflaredHandler != null ? cloudflaredHandler.getBinaryPath() : Constants.CLOUDFLARED_BINARY_NAME
        ));
    }

    protected AbstractManagedTunnel(CloudFlaredHandler cloudflaredHandler, ProcessHandler executor) {
        this.cfHandler = cloudflaredHandler;
        this.executor = executor;
    }

    protected synchronized boolean launchTunnelProcess(SubCommand command, Map<String, String> env) {
        return launchTunnelProcess(command, env, null);
    }

    protected synchronized boolean launchTunnelProcess(
        SubCommand command,
        Map<String, String> env,
        Collection<String> unsetEnvKeys
    ) {
        if (isTunnelRunning()) {
            Constants.LOG.debug("Tunnel process is already running.");
            return false;
        }
        stoppingIntentionally = false;
        edgeConnected = false;
        processExited = false;
        state = TunnelState.STARTING;
        startedAtMillis = System.currentTimeMillis();
        lastConnectionDetail = "Starting cloudflared tunnel process...";
        try {
            Process process = executor.runAsync(command, env, unsetEnvKeys, this::handleTunnelLogLine, this::handleTunnelLogLine);
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
                    } finally {
                        processExited = true;
                        synchronized (edgeConnectionLock) {
                            edgeConnectionLock.notifyAll();
                        }
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
            Constants.LOG.error("Failed to launch cloudflared process", e);
            return false;
        }
    }

    protected void handleTunnelLogLine(String line) {
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
            edgeConnected = true;
            synchronized (edgeConnectionLock) {
                edgeConnectionLock.notifyAll();
            }
        } else if (lower.contains("err ") || lower.contains("error") || lower.contains("failed")) {
            lastConnectionDetail = trimmed;
        }
    }

    @Override
    public boolean awaitEdgeConnection(long timeoutMillis, BooleanSupplier isStillAllowed) {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        synchronized (edgeConnectionLock) {
            while (true) {
                if (isStillAllowed != null && !isStillAllowed.getAsBoolean()) {
                    return false;
                }
                if (edgeConnected) {
                    return isTunnelRunning();
                }
                if (processExited || !isTunnelRunning()) {
                    return false;
                }
                long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
                if (remainingMs <= 0L) {
                    return false;
                }
                try {
                    edgeConnectionLock.wait(Math.min(remainingMs, 100L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    protected static String resolveLocalIp(String localIp) {
        if (localIp == null) {
            return "localhost";
        }
        String trimmed = localIp.trim();
        if (trimmed.isEmpty() || "0.0.0.0".equals(trimmed) || "::".equals(trimmed) || "[::]".equals(trimmed)) {
            return "localhost";
        }
        return trimmed;
    }

    protected static int resolveServerPort(int serverPort) {
        return serverPort > 0 ? serverPort : 25565;
    }

    public static String formatHostPort(String localIp, int serverPort) {
        String host = resolveLocalIp(localIp);
        int port = resolveServerPort(serverPort);
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            return host + ":" + port;
        }
        if (host.indexOf(':') >= 0) {
            return "[" + host + "]:" + port;
        }
        return host + ":" + port;
    }

    public static String formatTcpServiceUrl(String localIp, int serverPort) {
        return "tcp://" + formatHostPort(localIp, serverPort);
    }

    @Override
    public synchronized void terminateProcesses() {
        stoppingIntentionally = true;
        executor.terminate();
        activeTunnelProcess = null;
        edgeConnected = false;
        processExited = true;
        synchronized (edgeConnectionLock) {
            edgeConnectionLock.notifyAll();
        }
        state = TunnelState.STOPPED;
        startedAtMillis = 0L;
        lastConnectionDetail = "Stopped";
    }

    @Override
    public boolean isTunnelRunning() {
        Process proc = this.activeTunnelProcess;
        return proc != null && proc.isAlive();
    }

    @Override
    public TunnelState getState() {
        if (state == TunnelState.RUNNING && !isTunnelRunning()) {
            return stoppingIntentionally ? TunnelState.STOPPED : TunnelState.ERROR;
        }
        return state;
    }

    @Override
    public void setState(TunnelState state) {
        this.state = state;
    }

    @Override
    public void setLastConnectionDetail(String detail) {
        this.lastConnectionDetail = detail;
    }

    @Override
    public String getLastConnectionDetail() {
        return lastConnectionDetail;
    }

    @Override
    public long getActivePid() {
        Process proc = this.activeTunnelProcess;
        if (proc != null && proc.isAlive()) {
            return ProcessHandler.getProcessPid(proc);
        }
        return -1L;
    }

    @Override
    public long getUptimeMillis() {
        if (!isTunnelRunning() || startedAtMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - startedAtMillis);
    }
}
