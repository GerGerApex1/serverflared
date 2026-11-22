package me.gergerapex1.serverflared.utils.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.ModPlatformInstance;
import me.gergerapex1.serverflared.cloudflared.handler.TunnelMode;

public class ConfigManager {
    public static final String DEFAULT_HOST_PLACEHOLDER = "minecraftsubdomain.yourdomain.net";
    public static final String TUNNEL_STATE_FILENAME = "tunnel-state.yml";
    private static final String ZERO_UUID = new UUID(0, 0).toString();

    public boolean firstTime = false;
    private final YamlHandler handler = new YamlHandler();
    private final Path customConfigPath;
    private final Map<Config, String> configRevisions = new WeakHashMap<>();
    public Config CONFIG;

    public ConfigManager() {
        this(null);
    }

    public ConfigManager(Path customConfigPath) {
        this.customConfigPath = customConfigPath;
        Path configFilePath = getConfigFilePath();
        try {
            byte[] createdDefaultBytes = ensureConfigFileExists(configFilePath);
            if (createdDefaultBytes != null) {
                firstTime = true;
                CONFIG = new Config();
                recordRevision(CONFIG, sha256Hex(createdDefaultBytes));
                return;
            }

            // Read bytes first so filesystem I/O errors are never mistaken for empty/malformed YAML.
            byte[] rawBytes = Files.readAllBytes(configFilePath);
            if (rawBytes.length == 0) {
                Constants.LOG.warn("Config is empty, replacing with default");
                replaceWithDefault(configFilePath);
                return;
            }

            byte[] effectiveBytes;
            Config loaded;
            try {
                effectiveBytes = migrateConfigFile(configFilePath, rawBytes);
                loaded = handler.readFromYamlBytes(effectiveBytes, Config.class);
            } catch (JsonProcessingException | RuntimeException e) {
                Constants.LOG.warn("Failed to parse config, replacing with default ({})", e.getClass().getSimpleName());
                replaceWithDefault(configFilePath);
                return;
            }
            String revision = sha256Hex(effectiveBytes);

            if (!isConfigValid(loaded)) {
                Constants.LOG.warn(
                    "Config contains invalid values; preserving existing config.yml untouched and using safe defaults in memory."
                );
                CONFIG = new Config();
                firstTime = true;
                return;
            }

            applyPersistedTunnelIdentity(loaded, getTunnelStateFilePath(configFilePath));
            CONFIG = loaded;
            recordRevision(CONFIG, revision);
        } catch (IOException e) {
            Constants.LOG.error("Failed to load config ({})", e.getClass().getSimpleName());
            if (CONFIG == null) {
                CONFIG = new Config();
                firstTime = true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private byte[] migrateConfigFile(Path configFilePath, byte[] rawBytes) throws IOException {
        Map<String, Object> data = handler.readFromYamlBytes(rawBytes, Map.class);
        if (data == null) {
            return rawBytes;
        }

        boolean migrated = false;

        // Rename old key to new key
        if (data.containsKey("subdomain")) {
            data.put("hostName", data.remove("subdomain"));
            migrated = true;
        }

        // Add new required fields if missing
        Object versionObj = data.get("version");
        int currentVer = (versionObj instanceof Number) ? ((Number) versionObj).intValue() : 0;
        if (currentVer < Config.CURRENT_VERSION) {
            migrated = true;
        }

        if (!data.containsKey("commandPermissionMode")) {
            if (data.containsKey("permissionMode")) {
                data.put("commandPermissionMode", String.valueOf(data.remove("permissionMode")));
            } else if (data.containsKey("consoleOnlyCommands")) {
                Object val = data.remove("consoleOnlyCommands");
                boolean consoleOnly = Boolean.TRUE.equals(val) || "true".equalsIgnoreCase(String.valueOf(val));
                data.put("commandPermissionMode", consoleOnly ? "CONSOLE_ONLY" : "OP_OR_PERMISSION");
            } else {
                data.put("commandPermissionMode", "OP_OR_PERMISSION");
            }
            migrated = true;
        }

        if (!data.containsKey("tunnelMode")) {
            data.put("tunnelMode", TunnelMode.LOCAL.name());
            migrated = true;
        } else {
            Object rawTunnelMode = data.get("tunnelMode");
            String rawStr = rawTunnelMode != null ? String.valueOf(rawTunnelMode).trim() : "";
            if (!rawStr.isEmpty() && !TunnelMode.isValid(rawStr)) {
                Constants.LOG.warn(
                    "Invalid tunnelMode '{}' in config.yml; skipping migration to preserve existing config.yml.",
                    rawStr
                );
                return rawBytes;
            }
        }

        if (!data.containsKey("remote")) {
            migrated = true;
        }

        if (migrated) {
            // Rewrite file with migrated keys AND regenerated comments
            Config tempConfig = handler.readFromYamlBytes(rawBytes, Config.class);
            if (tempConfig != null) {
                tempConfig.setVersion(Config.CURRENT_VERSION);
                tempConfig.getRemote(); // ensure non-null for comment generation
                byte[] migratedBytes = handler.renderToYamlBytes(tempConfig);
                YamlHandler.writeBytesAtomically(configFilePath, migratedBytes);
                Constants.LOG.debug("Config migrated to version {} and comments regenerated", Config.CURRENT_VERSION);
                return migratedBytes;
            }
        }
        return rawBytes;
    }

    public synchronized Config loadCandidateConfig() {
        Path configFilePath = getConfigFilePath();
        try {
            ensureConfigFileExists(configFilePath);
            byte[] rawBytes = Files.readAllBytes(configFilePath);
            if (rawBytes.length == 0) {
                Constants.LOG.warn("Reloaded config is empty or contains invalid values.");
                return null;
            }
            byte[] effectiveBytes = migrateConfigFile(configFilePath, rawBytes);
            String revision = sha256Hex(effectiveBytes);
            Config reloaded = handler.readFromYamlBytes(effectiveBytes, Config.class);
            if (!isConfigValid(reloaded)) {
                Constants.LOG.warn("Reloaded config is empty or contains invalid values.");
                return null;
            }
            applyPersistedTunnelIdentity(reloaded, getTunnelStateFilePath(configFilePath));
            recordRevision(reloaded, revision);
            return reloaded;
        } catch (Exception e) {
            Constants.LOG.error("Failed to reload config ({})", e.getClass().getSimpleName());
            return null;
        }
    }

    public synchronized void applyConfig(Config config) {
        if (config == null) {
            return;
        }
        this.CONFIG = config;
        this.firstTime = isPendingFirstTimeSetup(config);
    }

    public synchronized boolean reload() {
        Config candidate = loadCandidateConfig();
        if (candidate == null) {
            return false;
        }
        applyConfig(candidate);
        return true;
    }

    public static boolean isPendingFirstTimeSetup(Config config) {
        if (config == null) {
            return true;
        }
        TunnelMode mode = config.getResolvedTunnelMode();
        if (mode == TunnelMode.REMOTE_TOKEN) {
            return config.getRemote().resolveTunnelToken().isEmpty();
        }
        if (mode == TunnelMode.REMOTE_API) {
            return config.getRemote().resolveApiToken().isEmpty() || isDefaultHostName(config.getHostName());
        }
        return isDefaultHostName(config.getHostName());
    }

    public static boolean isDefaultHostName(String host) {
        return host == null || host.trim().isEmpty() || DEFAULT_HOST_PLACEHOLDER.equalsIgnoreCase(host.trim());
    }

    public synchronized void saveConfig() {
        Path configFilePath = getConfigFilePath();
        try {
            byte[] writtenBytes = handler.renderToYamlBytes(CONFIG);
            YamlHandler.writeBytesAtomically(configFilePath, writtenBytes);
            recordRevision(CONFIG, sha256Hex(writtenBytes));
        } catch (IOException e) {
            Constants.LOG.error("Failed to save config: {}", e.getMessage());
        }
    }

    public synchronized boolean updateTunnelIdentityIfUnchanged(Config targetConfig, String tunnelId, String tunnelName) {
        if (targetConfig == null || this.CONFIG != targetConfig) {
            return false;
        }
        boolean idChanged = tunnelId != null && !tunnelId.isEmpty() && !tunnelId.equals(targetConfig.getTunnelId());
        boolean nameChanged = tunnelName != null && !tunnelName.isEmpty() && !tunnelName.equals(targetConfig.getTunnelName());
        if (!idChanged && !nameChanged) {
            return false;
        }

        // Persist automatic tunnel identity in a dedicated state file so operator-edited config.yml is never overwritten.
        Path stateFilePath = getTunnelStateFilePath(getConfigFilePath());
        String persistedId = idChanged ? tunnelId : targetConfig.getTunnelId();
        String persistedName = nameChanged ? tunnelName : targetConfig.getTunnelName();
        String persistedMode = targetConfig.getResolvedTunnelMode().name();

        Map<String, String> stateData = new LinkedHashMap<>();
        stateData.put("tunnelMode", persistedMode);
        stateData.put("tunnelName", persistedName != null ? persistedName : "");
        stateData.put("tunnelId", persistedId != null ? persistedId : "");
        try {
            byte[] stateBytes = handler.renderToYamlBytes(stateData);
            YamlHandler.writeBytesAtomically(stateFilePath, stateBytes);
            if (idChanged) {
                targetConfig.setTunnelId(tunnelId);
            }
            if (nameChanged) {
                targetConfig.setTunnelName(tunnelName);
            }
            return true;
        } catch (IOException e) {
            Constants.LOG.error("Failed to save tunnel identity state: {}", e.getMessage());
            return false;
        }
    }

    private static Path getTunnelStateFilePath(Path configFilePath) {
        return configFilePath.resolveSibling(TUNNEL_STATE_FILENAME);
    }

    @SuppressWarnings("unchecked")
    private void applyPersistedTunnelIdentity(Config config, Path stateFilePath) {
        if (config == null || stateFilePath == null || Files.notExists(stateFilePath)) {
            return;
        }
        String configuredId = config.getTunnelId();
        if (configuredId != null && !configuredId.trim().isEmpty() && !ZERO_UUID.equals(configuredId.trim())) {
            return;
        }
        try {
            Map<String, Object> stateData = handler.readFromYaml(stateFilePath.toString(), Map.class);
            if (stateData == null) {
                return;
            }
            String savedMode = stateData.get("tunnelMode") != null ? String.valueOf(stateData.get("tunnelMode")).trim() : "";
            String savedName = stateData.get("tunnelName") != null ? String.valueOf(stateData.get("tunnelName")).trim() : "";
            String savedId = stateData.get("tunnelId") != null ? String.valueOf(stateData.get("tunnelId")).trim() : "";
            if (savedId.isEmpty() || ZERO_UUID.equals(savedId)) {
                return;
            }
            if (!savedMode.isEmpty() && !savedMode.equalsIgnoreCase(config.getResolvedTunnelMode().name())) {
                return;
            }
            String configuredName = config.getTunnelName() != null ? config.getTunnelName().trim() : "";
            if (!savedName.isEmpty() && !configuredName.isEmpty() && !savedName.equals(configuredName)) {
                return;
            }
            config.setTunnelId(savedId);
        } catch (Exception e) {
            Constants.LOG.debug("Ignoring unreadable tunnel state file ({})", e.getClass().getSimpleName());
        }
    }

    private void recordRevision(Config config, String revision) {
        if (config != null && revision != null) {
            configRevisions.put(config, revision);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes != null ? bytes : new byte[0]);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    private Path getConfigFilePath() {
        if (customConfigPath != null) {
            return customConfigPath;
        }
        return Paths.get(ModPlatformInstance.xplat().getConfigDirectory().toString(),
            Constants.CONFIG_DIR, "config.yml");
    }

    private byte[] ensureConfigFileExists(Path configFilePath) throws IOException {
        Path parent = configFilePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.notExists(configFilePath)) {
            Config defaultConfig = new Config();
            byte[] defaultBytes = handler.renderToYamlBytes(defaultConfig);
            YamlHandler.writeBytesAtomically(configFilePath, defaultBytes);
            return defaultBytes;
        }
        return null;
    }

    private void replaceWithDefault(Path configFilePath) {
        try {
            Config defaultConfig = new Config();
            byte[] defaultBytes = handler.renderToYamlBytes(defaultConfig);
            YamlHandler.writeBytesAtomically(configFilePath, defaultBytes);
            CONFIG = defaultConfig;
            firstTime = true;
            recordRevision(defaultConfig, sha256Hex(defaultBytes));
        } catch (IOException e) {
            Constants.LOG.error("Failed to write default config: {}", e.getMessage());
        }
    }

    public static boolean isConfigValid(Config config) {
        if (config == null) {
            return false;
        }
        if (config.getCommandPermissionMode() == null || config.getCommandPermissionMode().trim().isEmpty()) {
            return false;
        }
        if (!TunnelMode.isValid(config.getTunnelMode())) {
            Constants.LOG.warn("Invalid tunnelMode '{}' in config.yml. Expected LOCAL, REMOTE_TOKEN, or REMOTE_API.",
                config.getTunnelMode());
            return false;
        }

        TunnelMode mode = config.getResolvedTunnelMode();
        switch (mode) {
            case LOCAL:
                return isNonEmpty(config.getTunnelName())
                    && isNonEmpty(config.getHostName())
                    && isNonEmpty(config.getTunnelId());
            case REMOTE_TOKEN:
                // Token may be provided via config.yml or SERVERFLARED_TUNNEL_TOKEN env var;
                // allow empty token on initial load so firstTime / reload can guide the user,
                // rather than wiping their config back to default.
                return true;
            case REMOTE_API:
                return isNonEmpty(config.getTunnelName())
                    && isNonEmpty(config.getHostName());
            default:
                return false;
        }
    }

    private static boolean isNonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
