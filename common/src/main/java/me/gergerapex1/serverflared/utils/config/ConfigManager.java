package me.gergerapex1.serverflared.utils.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.platform.Services;

public class ConfigManager {
    public boolean firstTime = false;
    private final YamlHandler handler = new YamlHandler();
    public Config CONFIG;
    
    public ConfigManager() {
        Path configFilePath = getConfigFilePath();
        try {
            createConfigFileIfNotExist(configFilePath);
            CONFIG = handler.readFromYaml(configFilePath.toString(), Config.class);
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
        }
    }
}
