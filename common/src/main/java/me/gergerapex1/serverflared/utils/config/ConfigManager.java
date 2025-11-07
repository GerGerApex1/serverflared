package me.gergerapex1.serverflared.utils.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.platform.Services;

public class ConfigManager {
    public boolean firstTime = false;
    private YamlHandler handler = new YamlHandler();
    public Config CONFIG;
    public ConfigManager() {
        Path configFilePath = Paths.get(Services.PLATFORM.getConfigDirectory().toString(), "cloudflared", "config.yml");
        try {
            _createConfigFileIfNotExist(configFilePath);
            CONFIG = handler.readFromYaml(configFilePath.toString(), Config.class);
        } catch (IOException e) {
            Constants.LOG.error(e.getMessage());
        }
    }
    public void saveConfig() {
        Path configFilePath = Paths.get(Services.PLATFORM.getConfigDirectory().toString(), "cloudflared", "config.yml");
        try {
            handler.writeToYaml(configFilePath.toString(), CONFIG);
        } catch (IOException e) {
            Constants.LOG.error(e.getMessage());
        }
    }
    private void _createConfigFileIfNotExist(Path configFilePath) throws IOException {
        Path parent = configFilePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent); // make parent dirs if needed
        }
        if (Files.notExists(configFilePath)) {
            Files.createFile(configFilePath); // create file if it doesn't exist
            firstTime = true;
            Config defaultConfig = new Config();
            handler.writeToYaml(configFilePath.toString(), defaultConfig);
        }
    }
}
