package me.gergerapex1.serverflared.cloudflared.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.ModPlatformInstance;
import me.gergerapex1.serverflared.process.ProcessHandler;
import me.gergerapex1.serverflared.process.SubCommand;
import me.gergerapex1.serverflared.utils.config.Config;
import me.gergerapex1.serverflared.utils.config.YamlHandler;

public class LocalManagedTunnel extends AbstractManagedTunnel {
    private static final String ZERO_UUID = new UUID(0, 0).toString();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    static final List<String> LOCAL_UNSET_ENV_KEYS = Collections.unmodifiableList(
        Arrays.asList("TUNNEL_TOKEN", "TUNNEL_TOKEN_FILE")
    );
    private static final Pattern EXACT_UUID_REGEX = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final Path customConfigPath;
    private final String tunnelConfigLocation;
    private final YamlHandler yamlHandler;

    public LocalManagedTunnel(CloudFlaredHandler cloudflaredHandler) {
        super(cloudflaredHandler);
        this.customConfigPath = null;
        Path cfgPath = getOrCreateTunnelConfig();
        this.tunnelConfigLocation = cfgPath != null ? cfgPath.toString() : "";
        this.yamlHandler = new YamlHandler();
    }

    LocalManagedTunnel(CloudFlaredHandler cloudflaredHandler, ProcessHandler customExecutor, Path customConfigPath) {
        super(cloudflaredHandler, customExecutor);
        this.customConfigPath = customConfigPath;
        this.tunnelConfigLocation = customConfigPath != null ? customConfigPath.toString() : "";
        this.yamlHandler = new YamlHandler();
    }

    @Override
    public TunnelMode getMode() {
        return TunnelMode.LOCAL;
    }

    @Override
    public boolean requiresCliAuth() {
        return true;
    }

    @Override
    public TunnelInfo prepareTunnel(Config config, String localIp, int serverPort) {
        return prepareTunnel(config, localIp, serverPort, null);
    }

    @Override
    public TunnelInfo prepareTunnel(
        Config config,
        String localIp,
        int serverPort,
        WriteCommitCoordinator writeCoordinator
    ) {
        if (config == null || cfHandler == null || !cfHandler.isAuthenticated()) {
            state = TunnelState.ERROR;
            lastConnectionDetail = "Cloudflared is not authenticated";
            return null;
        }
        TunnelInfo initialTunnelInfo = new TunnelInfo();
        initialTunnelInfo.setName(config.getTunnelName());
        initialTunnelInfo.setId(config.getTunnelId());

        CloudFlaredHandler.TunnelLookupStatus lookupStatus = cfHandler.lookupTunnelStatus(initialTunnelInfo);
        Constants.LOG.debug("Tunnel lookup status for '{}' ({}): {}",
            initialTunnelInfo.getName(), initialTunnelInfo.getId(), lookupStatus);

        final TunnelInfo verifiedTunnel;
        if (lookupStatus == CloudFlaredHandler.TunnelLookupStatus.ERROR) {
            Constants.LOG.error("Failed to determine whether tunnel '{}' exists; aborting preparation without creating a new tunnel.",
                initialTunnelInfo.getName());
            state = TunnelState.ERROR;
            lastConnectionDetail = "Failed to query tunnel existence";
            return null;
        } else if (lookupStatus == CloudFlaredHandler.TunnelLookupStatus.NOT_FOUND) {
            Constants.LOG.debug("Confirmed tunnel '{}' ({}) does not exist; creating new tunnel",
                initialTunnelInfo.getName(), initialTunnelInfo.getId());

            TunnelInfo createdTunnel = createTunnel(initialTunnelInfo);
            if (!isVerifiedTunnel(createdTunnel)) {
                Constants.LOG.error("Failed to create or verify tunnel for name '{}' and ID '{}'. Aborting tunnel preparation.",
                    initialTunnelInfo.getName(), initialTunnelInfo.getId());
                if (state != TunnelState.ERROR) {
                    state = TunnelState.ERROR;
                    lastConnectionDetail = "Failed to create or verify local tunnel";
                }
                return null;
            }
            verifiedTunnel = createdTunnel;
            Constants.LOG.info("Tunnel ready with verified ID {}", verifiedTunnel.getId());
        } else {
            TunnelInfo existingTunnel = lookupVerifiedExistingTunnel(initialTunnelInfo);
            if (!isVerifiedTunnel(existingTunnel)) {
                Constants.LOG.error("Existing tunnel lookup did not return a verified tunnel for name '{}' and ID '{}'. Aborting tunnel preparation.",
                    initialTunnelInfo.getName(), initialTunnelInfo.getId());
                state = TunnelState.ERROR;
                lastConnectionDetail = "Failed to verify existing local tunnel ID";
                return null;
            }
            Constants.LOG.info("Tunnel with name {} and ID {} found",
                existingTunnel.getName(), existingTunnel.getId());
            verifiedTunnel = existingTunnel;
        }

        BooleanSupplier writeAction = () -> {
            if (!routeDnsToTunnel(verifiedTunnel.getId(), config.getHostName())) {
                Constants.LOG.error("Failed to verify or create DNS route for '{}' -> tunnel {}. Aborting tunnel preparation.",
                    config.getHostName(), verifiedTunnel.getId());
                if (state != TunnelState.ERROR) {
                    state = TunnelState.ERROR;
                    lastConnectionDetail = "Failed to configure DNS route for " + config.getHostName();
                }
                return false;
            }

            boolean configWritten = createTunnelConfig(verifiedTunnel.getId(), localIp, config.getHostName(), serverPort);
            if (!configWritten) {
                Constants.LOG.error("Failed to write tunnel YAML configuration for tunnel ID {}. Aborting tunnel preparation.",
                    verifiedTunnel.getId());
                state = TunnelState.ERROR;
                lastConnectionDetail = "Failed to write local tunnel configuration";
                return false;
            }
            return true;
        };

        boolean committed = (writeCoordinator != null)
            ? writeCoordinator.executeIfOwner(writeAction)
            : writeAction.getAsBoolean();
        if (!committed) {
            return null;
        }
        return verifiedTunnel;
    }

    @Override
    public boolean startTunnel(Config config, TunnelInfo info) {
        if (!isVerifiedTunnel(info)) {
            Constants.LOG.error("Cannot start local tunnel: TunnelInfo does not have a verified tunnel ID.");
            state = TunnelState.ERROR;
            lastConnectionDetail = "Cannot start tunnel without a verified tunnel ID";
            return false;
        }
        return runLocalTunnel(info.getId());
    }

    public TunnelInfo createTunnel(TunnelInfo tunnelInfo) {
        if (tunnelInfo == null || tunnelInfo.getName() == null || tunnelInfo.getName().trim().isEmpty()) {
            Constants.LOG.error("Cannot create tunnel: tunnel name is null or empty.");
            state = TunnelState.ERROR;
            lastConnectionDetail = "Tunnel name is empty";
            return null;
        }

        final String requestedName = tunnelInfo.getName().trim();
        final AtomicBoolean alreadyExists = new AtomicBoolean(false);
        final StringBuilder outputBuffer = new StringBuilder();

        Process process = executor.run(
            new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_CREATE, "--output", "json", requestedName),
            line -> {
                if (line == null) {
                    return;
                }
                Constants.LOG.debug(line);
                if (tunnelAlreadyExist(line)) {
                    Constants.LOG.debug("Cannot create tunnel. Tunnel with name {} already exists", requestedName);
                    alreadyExists.set(true);
                }
                synchronized (outputBuffer) {
                    if (outputBuffer.length() > 0) {
                        outputBuffer.append('\n');
                    }
                    outputBuffer.append(line);
                }
            },
            errLine -> {
                if (errLine == null) {
                    return;
                }
                Constants.LOG.error(errLine);
                if (tunnelAlreadyExist(errLine)) {
                    alreadyExists.set(true);
                }
            }
        );

        if (process == null) {
            Constants.LOG.error("Failed to execute 'cloudflared tunnel create --output json {}'", requestedName);
            state = TunnelState.ERROR;
            lastConnectionDetail = "Failed to execute tunnel creation command";
            return null;
        }

        int exitCode = process.exitValue();
        if (exitCode == 0) {
            String rawOutput;
            synchronized (outputBuffer) {
                rawOutput = outputBuffer.toString();
            }
            String createdId = parseCreatedTunnelIdFromJson(rawOutput);
            if (isVerifiedTunnelId(createdId)) {
                Constants.LOG.info("Created tunnel '{}' with ID: {}", requestedName, createdId);
                TunnelInfo created = new TunnelInfo();
                created.setId(createdId);
                created.setName(requestedName);
                tunnelInfo.setTunnelAndId(created);
                return created;
            }
            Constants.LOG.warn("Tunnel creation exited with code 0 for '{}', but structured JSON 'id' could not be extracted; falling back to lookup.", requestedName);
            TunnelInfo fallback = lookupVerifiedExistingTunnel(tunnelInfo);
            if (isVerifiedTunnel(fallback)) {
                tunnelInfo.setTunnelAndId(fallback);
                return fallback;
            }
            Constants.LOG.error("Tunnel creation exited with code 0 for '{}', but neither JSON output parsing nor fallback lookup produced a verified tunnel ID.", requestedName);
            state = TunnelState.ERROR;
            lastConnectionDetail = "Tunnel created but failed to verify tunnel ID";
            return null;
        }

        if (alreadyExists.get()) {
            Constants.LOG.info("Tunnel '{}' already exists (exit code {}); looking up existing tunnel...", requestedName, exitCode);
            TunnelInfo existingTunnel = lookupVerifiedExistingTunnel(tunnelInfo);
            if (isVerifiedTunnel(existingTunnel)) {
                Constants.LOG.info("Using verified existing tunnel '{}', ID {}", existingTunnel.getName(), existingTunnel.getId());
                tunnelInfo.setTunnelAndId(existingTunnel);
                return existingTunnel;
            }
            Constants.LOG.error("Tunnel '{}' already exists, but fallback lookup failed to return a verified tunnel.", requestedName);
            state = TunnelState.ERROR;
            lastConnectionDetail = "Tunnel already exists, but fallback lookup failed";
            return null;
        }

        Constants.LOG.error("Failed to create tunnel '{}' (exit code {}). Refusing to accept unverified tunnel ID.", requestedName, exitCode);
        state = TunnelState.ERROR;
        lastConnectionDetail = "Tunnel creation failed (exit code " + exitCode + ")";
        return null;
    }

    static String parseCreatedTunnelIdFromJson(String rawOutput) {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            return null;
        }
        String trimmed = rawOutput.trim();
        String direct = extractIdFieldIfJsonObject(trimmed);
        if (direct != null) {
            return direct;
        }

        // If cloudflared emitted non-JSON log lines alongside the JSON object (e.g. merged stderr),
        // scan candidate JSON object slices starting at '{' and ending at '}'.
        int start = trimmed.indexOf('{');
        while (start >= 0) {
            int end = trimmed.lastIndexOf('}');
            while (end > start) {
                String candidate = trimmed.substring(start, end + 1);
                String extracted = extractIdFieldIfJsonObject(candidate);
                if (extracted != null) {
                    return extracted;
                }
                end = trimmed.lastIndexOf('}', end - 1);
            }
            start = trimmed.indexOf('{', start + 1);
        }
        return null;
    }

    private static String extractIdFieldIfJsonObject(String jsonText) {
        try {
            JsonNode node = JSON_MAPPER.readTree(jsonText);
            if (node != null && node.isObject()) {
                JsonNode idNode = node.get("id");
                if (idNode != null && idNode.isTextual()) {
                    String id = idNode.asText().trim();
                    if (isVerifiedTunnelId(id)) {
                        return id;
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private TunnelInfo lookupVerifiedExistingTunnel(TunnelInfo query) {
        if (cfHandler == null || query == null) {
            return null;
        }
        TunnelInfo existing = cfHandler.getTunnelInfo(query);
        return isVerifiedTunnel(existing) ? existing : null;
    }

    public static boolean isVerifiedTunnel(TunnelInfo info) {
        return info != null && isVerifiedTunnelId(info.getId());
    }

    public static boolean isVerifiedTunnelId(String tunnelId) {
        if (tunnelId == null) {
            return false;
        }
        String trimmed = tunnelId.trim();
        if (trimmed.isEmpty() || ZERO_UUID.equalsIgnoreCase(trimmed)) {
            return false;
        }
        return EXACT_UUID_REGEX.matcher(trimmed).matches();
    }

    public boolean routeDnsToTunnel(String tunnelId, String routeDnsDomain) {
        if (!isVerifiedTunnelId(tunnelId)) {
            Constants.LOG.error("Refusing to route DNS for unverified or zero tunnel UUID: '{}'", tunnelId);
            state = TunnelState.ERROR;
            lastConnectionDetail = "Unverified tunnel ID for DNS routing";
            return false;
        }
        if (routeDnsDomain == null || routeDnsDomain.trim().isEmpty()) {
            Constants.LOG.error("Refusing to route DNS: hostname is empty.");
            state = TunnelState.ERROR;
            lastConnectionDetail = "Hostname is empty for DNS routing";
            return false;
        }

        final String trimmedDomain = routeDnsDomain.trim();
        final AtomicBoolean alreadyConfiguredForTunnel = new AtomicBoolean(false);
        final AtomicBoolean dnsConflict = new AtomicBoolean(false);
        final AtomicReference<String> lastOutputLine = new AtomicReference<>("");

        java.util.function.Consumer<String> lineConsumer = line -> {
            if (line == null || line.trim().isEmpty()) {
                return;
            }
            String trimmed = line.trim();
            lastOutputLine.set(trimmed);
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.contains("already configured to route to your tunnel")
                || lower.contains("already routed to this tunnel")) {
                alreadyConfiguredForTunnel.set(true);
            } else if (lower.contains("already exists")
                || lower.contains("code: 1003")
                || lower.contains("81053")) {
                dnsConflict.set(true);
            }
        };

        // Intentionally omit --overwrite-dns so conflicting DNS records are never overwritten.
        Process process = executor.run(
            new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_ROUTE, Constants.CMD_DNS, tunnelId, trimmedDomain),
            lineConsumer,
            lineConsumer
        );

        if (process == null) {
            Constants.LOG.error("Failed to execute 'cloudflared tunnel route dns' for '{}'", trimmedDomain);
            state = TunnelState.ERROR;
            lastConnectionDetail = "Failed to execute DNS route command for " + trimmedDomain;
            return false;
        }

        int exitCode = process.exitValue();
        if (exitCode == 0 || alreadyConfiguredForTunnel.get()) {
            Constants.LOG.debug("DNS route verified/configured for '{}' -> tunnel {}", trimmedDomain, tunnelId);
            return true;
        }

        if (dnsConflict.get()) {
            Constants.LOG.error(
                "DNS route conflict for '{}': an existing DNS record already exists and will NOT be overwritten. "
                    + "Verify or remove the conflicting record in Cloudflare DNS, then reload.",
                trimmedDomain
            );
            state = TunnelState.ERROR;
            lastConnectionDetail = "DNS conflict for " + trimmedDomain + " (existing record not overwritten)";
            return false;
        }

        Constants.LOG.error("Failed to route DNS '{}' -> tunnel {} (exit code {}): {}",
            trimmedDomain, tunnelId, exitCode, lastOutputLine.get());
        state = TunnelState.ERROR;
        lastConnectionDetail = "DNS route failed for " + trimmedDomain + " (exit code " + exitCode + ")";
        return false;
    }

    public synchronized boolean runLocalTunnel(String tunnelIdOrName) {
        return launchTunnelProcess(
            new SubCommand(Constants.CMD_TUNNEL, "--config", "\"" + tunnelConfigLocation + "\"", Constants.CMD_RUN, tunnelIdOrName),
            null,
            LOCAL_UNSET_ENV_KEYS
        );
    }

    public boolean createTunnelConfig(String tunnelId, String localIp, String hostname, int serverPort) {
        if (!isVerifiedTunnelId(tunnelId)) {
            Constants.LOG.error("Refusing to write tunnel YAML config with unverified or zero tunnel UUID: '{}'", tunnelId);
            return false;
        }
        String serviceUrl = formatTcpServiceUrl(localIp, serverPort);
        Constants.LOG.debug("Creating tunnel config for tunnelId: {}, serviceUrl: {}, hostname: {}",
            tunnelId, serviceUrl, hostname);
        TunnelConfig config = new TunnelConfig(hostname, serviceUrl, tunnelId);
        Path configPath = getOrCreateTunnelConfig();
        if (configPath == null) {
            return false;
        }
        try {
            yamlHandler.overwriteFileWithYaml(configPath.toString(), config);
            return true;
        } catch (IOException e) {
            Constants.LOG.error(e.getMessage());
            return false;
        }
    }

    public Path getOrCreateTunnelConfig() {
        Path configFilePath = (customConfigPath != null)
            ? customConfigPath
            : Paths.get(
                ModPlatformInstance.xplat().getConfigDirectory().toAbsolutePath().toString(),
                Constants.CONFIG_DIR,
                Constants.TUNNEL_CONFIG_FILENAME
            );
        try {
            Path parent = configFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.notExists(configFilePath)) {
                Files.createFile(configFilePath);
            }
        } catch (IOException e) {
            Constants.LOG.error(e.getMessage());
            return null;
        }
        return configFilePath;
    }

    public boolean tunnelAlreadyExist(String line) {
        return line != null && line.toLowerCase().contains("tunnel with name already exist");
    }
}
