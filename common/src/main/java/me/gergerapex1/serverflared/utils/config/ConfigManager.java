package me.gergerapex1.serverflared.utils.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.ModPlatformInstance;
import me.gergerapex1.serverflared.cloudflared.handler.TunnelMode;

public class ConfigManager {
    public static final String DEFAULT_HOST_PLACEHOLDER = "minecraftsubdomain.yourdomain.net";

    public boolean firstTime = false;
    private final YamlHandler handler = new YamlHandler();
    private final Path customConfigPath;
    public Config CONFIG;

    public ConfigManager() {
        this(null);
    }

    public ConfigManager(Path customConfigPath) {
        this.customConfigPath = customConfigPath;
        Path configFilePath = getConfigFilePath();
        try {
            createConfigFileIfNotExist(configFilePath);
            migrateConfigFile(configFilePath);

            try {
                CONFIG = handler.readFromYaml(configFilePath.toString(), Config.class);
            } catch (Exception e) {
                Constants.LOG.warn("Failed to parse config, replacing with default ({})", e.getClass().getSimpleName());
                replaceWithDefault(configFilePath);
                return;
            }

            if (!isConfigValid(CONFIG) || isFileEmpty(configFilePath)) {
                Constants.LOG.warn("Config is empty or contains invalid values, replacing with default");
                replaceWithDefault(configFilePath);
            }
        } catch (IOException e) {
            Constants.LOG.error("Failed to load config ({})", e.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    private void migrateConfigFile(Path configFilePath) throws IOException {
        Map<String, Object> data = handler.readFromYaml(configFilePath.toString(), Map.class);
        if (data == null) {
            return;
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
        }

        if (!data.containsKey("remote")) {
            migrated = true;
        }

        if (migrated) {
            // Rewrite file with migrated keys AND regenerated comments
            Config tempConfig = handler.readFromYaml(configFilePath.toString(), Config.class);
            if (tempConfig != null) {
                tempConfig.setVersion(Config.CURRENT_VERSION);
                tempConfig.getRemote(); // ensure non-null for comment generation
                handler.writeToYaml(configFilePath.toString(), tempConfig);
                Constants.LOG.debug("Config migrated to version {} and comments regenerated", Config.CURRENT_VERSION);
            }
        }
    }

    public synchronized Config loadCandidateConfig() {
        Path configFilePath = getConfigFilePath();
        try {
            createConfigFileIfNotExist(configFilePath);
            migrateConfigFile(configFilePath);
            Config reloaded = handler.readFromYaml(configFilePath.toString(), Config.class);
            if (!isConfigValid(reloaded) || isFileEmpty(configFilePath)) {
                Constants.LOG.warn("Reloaded config is empty or contains invalid values.");
                return null;
            }
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
            handler.writeToYaml(configFilePath.toString(), CONFIG);
        } catch (IOException e) {
            Constants.LOG.error("Failed to save config: {}", e.getMessage());
        }
    }

    private Path getConfigFilePath() {
        if (customConfigPath != null) {
            return customConfigPath;
        }
        return Paths.get(ModPlatformInstance.xplat().getConfigDirectory().toString(),
            Constants.CONFIG_DIR, "config.yml");
    }

    private void createConfigFileIfNotExist(Path configFilePath) throws IOException {
        Path parent = configFilePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.notExists(configFilePath)) {
            Files.createFile(configFilePath);
            firstTime = true;
            Config defaultConfig = new Config();
            handler.writeToYaml(configFilePath.toString(), defaultConfig);
            CONFIG = defaultConfig;
        }
    }

    private boolean isFileEmpty(Path path) {
        try {
            return Files.size(path) == 0;
        } catch (IOException e) {
            return true;
        }
    }

    private void replaceWithDefault(Path configFilePath) {
        try {
            Config defaultConfig = new Config();
            handler.writeToYaml(configFilePath.toString(), defaultConfig);
            CONFIG = defaultConfig;
            firstTime = true;
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
