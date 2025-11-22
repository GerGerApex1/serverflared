package me.gergerapex1.serverflared.cloudflared.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.platform.Services;
import me.gergerapex1.serverflared.utils.config.YamlHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LocalManagedTunnel {
    static final Logger logger = LogManager.getLogger("ServerGotFlared/LocallyManagedTunnel");
    private final Pattern uuidRegex = Pattern.compile(
        "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
    private final CloudflaredProcessHandler executor;
    private final String tunnelConfigLocation;
    private final YamlHandler yamlHandler;

    public LocalManagedTunnel(CloudFlaredHandler cloudflaredHandler) {
        String binaryLocation = cloudflaredHandler.getBinaryPath();
        this.executor = new CloudflaredProcessHandler(binaryLocation);
        this.tunnelConfigLocation = getOrCreateTunnelConfig().toString();
        this.yamlHandler = new YamlHandler();
    }
    public TunnelInfo createTunnel(TunnelInfo tunnelInfo) {
        try {
            Process tunnelCreation = executor.executeCommandAsync(Constants.CMD_TUNNEL, Constants.CMD_CREATE, tunnelInfo.getName());
            BufferedReader reader = new BufferedReader(new InputStreamReader(tunnelCreation.getInputStream()));
            String line;
            String tunnelId;
            while ((line = reader.readLine()) != null) {
                Constants.LOG.debug(line);
                Matcher m = uuidRegex.matcher(line);
                System.out.println(line);
                if (m.find()) {
                    tunnelId = m.group(1);
                    Constants.LOG.info("Created tunnel with ID: {}", tunnelId);
                    createTunnelConfig(tunnelId, tunnelId);
                    tunnelInfo.setId(tunnelId);
                }
                if (line.toLowerCase().contains("tunnel with name already exists")) {
                    return null;
                }
            }
            tunnelCreation.waitFor();
            Constants.LOG.info("Created a tunnel");
            return tunnelInfo;
        } catch (IOException | InterruptedException e){
            logger.info(e);
        }
        return null;
    }
    
    public void routeDnsToTunnel(String tunnelNameOrName, String routeDnsDomain) {
        executor.executeCommand(Constants.CMD_TUNNEL, Constants.CMD_ROUTE, Constants.CMD_DNS, tunnelNameOrName, routeDnsDomain);
    }
    
    public void runLocalTunnel(String tunnelIdOrName) {
        try {
            logger.info(tunnelConfigLocation);
            executor.executeCommandAsync(Constants.CMD_TUNNEL, "--config", tunnelConfigLocation, Constants.CMD_RUN, tunnelIdOrName);
        } catch (IOException e) {
            logger.error(e);
        }
    }
    
    public void createTunnelConfig(String tunnelId, String uuid) {
        TunnelConfig config = new TunnelConfig(tunnelId, uuid);
        Path configPath = getOrCreateTunnelConfig();
        config.setUrl(Constants.DEFAULT_TUNNEL_URL);
        try {
            yamlHandler.writeToYaml(configPath.toString(), config);
        } catch (IOException e) {
            logger.error(e);
        }
    }
    
    public Path getOrCreateTunnelConfig() {
        Path configFilePath = Paths.get(
            Services.PLATFORM.getConfigDirectory().toAbsolutePath().toString(), 
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
            logger.error(e.getMessage());
            return null;
        }
        return configFilePath;
    }
    public void terminate() {
        executor.terminate();
    }
}
