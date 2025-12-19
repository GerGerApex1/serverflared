package me.gergerapex1.serverflared.cloudflared.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.ModPlatformInstance;
import me.gergerapex1.serverflared.process.ProcessHandler;
import me.gergerapex1.serverflared.process.SubCommand;
import me.gergerapex1.serverflared.utils.config.YamlHandler;

public class LocalManagedTunnel {
    private final Pattern uuidRegex = Pattern.compile(
        "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
    private final ProcessHandler executor;
    private final String tunnelConfigLocation;
    private final YamlHandler yamlHandler;
    CloudFlaredHandler cfHandler;

    public LocalManagedTunnel(CloudFlaredHandler cloudflaredHandler) {
        cfHandler = cloudflaredHandler;
        this.executor = new ProcessHandler(cfHandler.getBinaryPath());
        this.tunnelConfigLocation = getOrCreateTunnelConfig().toString();
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
            process.waitFor();
            if(createdTunnelRef.get() == null) {
                Constants.LOG.info("Getting existing tunnel with name and ID: {}, {}", tunnelInfo.getName(), tunnelInfo.getId());
                TunnelInfo existingTunnel = cfHandler.getTunnelInfo(tunnelInfo);
                Constants.LOG.info("Using existing tunnel, {}, {}", existingTunnel.getName(), existingTunnel.getId());
                tunnelInfo.setTunnelAndId(tunnelInfo);
            } else {
                tunnelInfo.setId(createdTunnelRef.get().getId());
                tunnelInfo.setName(createdTunnelRef.get().getName());
            }
            return tunnelInfo;
        } catch (InterruptedException e) {
            Constants.LOG.error(e.getMessage());
            return null;
        }
    }
    public void routeDnsToTunnel(String tunnelNameOrName, String routeDnsDomain) {
        executor.run(new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_ROUTE, Constants.CMD_DNS, tunnelNameOrName, routeDnsDomain), null, null);
    }

    public void runLocalTunnel(String tunnelIdOrName) {
        try {
            executor.runAsync(new SubCommand(Constants.CMD_TUNNEL, "--config", tunnelConfigLocation, Constants.CMD_RUN, tunnelIdOrName), null, Constants.LOG::info);
        } catch (IOException e) {
            Constants.LOG.error(e.getMessage());
        }
    }

    public void createTunnelConfig(String tunnelId, String serverIp, int serverPort) {
        TunnelConfig config = new TunnelConfig("tcp://"+serverIp+":"+serverPort, tunnelId);
        Path configPath = getOrCreateTunnelConfig();
        config.setUrl(Constants.DEFAULT_TUNNEL_URL);
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
    public void terminate() {
        executor.terminate();
    }
    public boolean tunnelAlreadyExist(String line) {
        return line.toLowerCase().contains("tunnel with name already exist");
    }
}
