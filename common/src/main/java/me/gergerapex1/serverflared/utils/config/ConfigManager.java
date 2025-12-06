package me.gergerapex1.serverflared.utils.config;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.platform.Services;
import me.gergerapex1.serverflared.utils.config.annonations.Comment;

public class ConfigManager {
    public boolean firstTime = false;
    private final YamlHandler handler = new YamlHandler();
    public Config CONFIG;

    public ConfigManager() {
        Path configFilePath = getConfigFilePath();
        try {
            createConfigFileIfNotExist(configFilePath);

            try {
                CONFIG = handler.readFromYaml(configFilePath.toString(), Config.class);
            } catch (Exception e) {
                Constants.LOG.warn("Failed to parse config, replacing with default: {}", e.getMessage());
                replaceWithDefault(configFilePath);
                return;
            }

            if (!isConfigValid(CONFIG) || isFileEmpty(configFilePath)) {
                Constants.LOG.warn("Config is empty or contains invalid values, replacing with default");
                replaceWithDefault(configFilePath);
            }
        } catch (IOException e) {
            Constants.LOG.error("Failed to load config: {}", e.getMessage());
        }
    }

    public void saveConfig() {
        Path configFilePath = getConfigFilePath();
        try {
            handler.writeToYaml(configFilePath.toString(), CONFIG);
        } catch (IOException e) {
            Constants.LOG.error("Failed to save config: {}", e.getMessage());
        }
    }

    private Path getConfigFilePath() {
        return Paths.get(Services.PLATFORM.getConfigDirectory().toString(),
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

    private boolean isConfigValid(Config config) {
        if (config == null) return false;
        try {
            for (Field field : Config.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(config);
                if (value == null) return false;
                if (value instanceof String && ((String) value).trim().isEmpty()) return false;
            }
            return true;
        } catch (IllegalAccessException e) {
            return false;
        }
    }
}
