package me.gergerapex1.serverflared.cloudflared.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.html.Option;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.process.ProcessHandler;
import me.gergerapex1.serverflared.platform.Services;
import me.gergerapex1.serverflared.process.SubCommand;
import me.gergerapex1.serverflared.utils.config.YamlHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LocalManagedTunnel {
    static final Logger logger = LogManager.getLogger("ServerGotFlared/LocallyManagedTunnel");
    private final Pattern uuidRegex = Pattern.compile(
        "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
    private final ProcessHandler executor;
    private final String tunnelConfigLocation;
    private final YamlHandler yamlHandler;

    public LocalManagedTunnel(CloudFlaredHandler cloudflaredHandler) {
        String binaryLocation = cloudflaredHandler.getBinaryPath();
        this.executor = new ProcessHandler(binaryLocation);
        this.tunnelConfigLocation = getOrCreateTunnelConfig().toString();
        this.yamlHandler = new YamlHandler();
    }
    public TunnelInfo createTunnel(TunnelInfo tunnelInfo) {
        try {
            Process process = executor.executeCommandAsync(new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_CREATE, tunnelInfo.getName()));
            Optional<TunnelInfo> output = executor.captureProcessOutput(process, line -> {
                if (tunnelAlreadyExist(line)) {
                    Constants.LOG.debug("Cannot create tunnel. Tunnel with name {} already exists", tunnelInfo.getName());
                    return Optional.of(tunnelInfo);
                }
                Constants.LOG.debug(line);
                Matcher m = uuidRegex.matcher(line);
                if (m.find()) {
                    String group = m.group(1);
                    Constants.LOG.info("Created tunnel with ID: {}", group);
                    createTunnelConfig(group, group);
                    tunnelInfo.setId(group);
                }
                return Optional.of(tunnelInfo);
            });
            output.ifPresent(info -> {
                tunnelInfo.setId(info.getId());
                tunnelInfo.setName(info.getName());
            });
            return tunnelInfo;
        } catch (IOException e) {
            Constants.LOG.error(e);
            return null;
        }
    }

    public void routeDnsToTunnel(String tunnelNameOrName, String routeDnsDomain) {
        executor.executeCommand(new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_ROUTE, Constants.CMD_DNS, tunnelNameOrName, routeDnsDomain));
    }

    public void runLocalTunnel(String tunnelIdOrName) {
        try {
            executor.executeCommandAsync(new SubCommand(Constants.CMD_TUNNEL, "--config", tunnelConfigLocation, Constants.CMD_RUN, tunnelIdOrName));
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
    public boolean tunnelAlreadyExist(String line) {
        return line.toLowerCase().contains("tunnel with name already exist");
    }
}
