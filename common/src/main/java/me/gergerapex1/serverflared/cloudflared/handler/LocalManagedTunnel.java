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
import me.gergerapex1.serverflared.process.SubCommand;
import me.gergerapex1.serverflared.utils.config.Config;
import me.gergerapex1.serverflared.utils.config.YamlHandler;

public class LocalManagedTunnel extends AbstractManagedTunnel {
    private static final Pattern UUID_REGEX = Pattern.compile(
        "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");

    private final String tunnelConfigLocation;
    private final YamlHandler yamlHandler;

    public LocalManagedTunnel(CloudFlaredHandler cloudflaredHandler) {
        super(cloudflaredHandler);
        Path cfgPath = getOrCreateTunnelConfig();
        this.tunnelConfigLocation = cfgPath != null ? cfgPath.toString() : "";
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
        if (cfHandler == null || !cfHandler.isAuthenticated()) {
            return null;
        }
        TunnelInfo initialTunnelInfo = new TunnelInfo();
        initialTunnelInfo.setName(config.getTunnelName());
        initialTunnelInfo.setId(config.getTunnelId());

        boolean tunnelExists = cfHandler.validateTunnelExist(initialTunnelInfo);
        Constants.LOG.debug("Is tunnel exist: {}", tunnelExists);
        if (!tunnelExists) {
            Constants.LOG.debug("Tunnel with name {} and ID {} not found, creating new tunnel",
                initialTunnelInfo.getName(), initialTunnelInfo.getId());

            TunnelInfo createdTunnel = createTunnel(initialTunnelInfo);
            if (createdTunnel == null) {
                Constants.LOG.error("Failed to create tunnel for name {} and ID {}. Aborting tunnel initiation.",
                    initialTunnelInfo.getName(), initialTunnelInfo.getId());
                return null;
            }
            routeDnsToTunnel(createdTunnel.getId(), config.getHostName());
            initialTunnelInfo = createdTunnel;
            Constants.LOG.info("New tunnel created with ID {}", createdTunnel.getId());
        } else {
            TunnelInfo existingTunnel = cfHandler.getTunnelInfo(initialTunnelInfo);
            if (existingTunnel != null) {
                Constants.LOG.info("Tunnel with name {} and ID {} found",
                    existingTunnel.getName(), existingTunnel.getId());
                initialTunnelInfo = existingTunnel;
            }
        }

        String resolvedId = (initialTunnelInfo.getId() != null && !initialTunnelInfo.getId().isEmpty())
            ? initialTunnelInfo.getId()
            : config.getTunnelId();
        createTunnelConfig(resolvedId, localIp, config.getHostName(), serverPort);
        return initialTunnelInfo;
    }

    @Override
    public boolean startTunnel(Config config, TunnelInfo info) {
        String tunnelId = (info != null && info.getId() != null && !info.getId().isEmpty())
            ? info.getId()
            : (config != null ? config.getTunnelId() : "");
        String runTarget = (tunnelId != null && !tunnelId.isEmpty()
            && (cfHandler == null || !cfHandler.isDefaultTunnelUUID(tunnelId)))
            ? tunnelId
            : (config != null ? config.getTunnelName() : "");
        return runLocalTunnel(runTarget);
    }

    public TunnelInfo createTunnel(TunnelInfo tunnelInfo) {
        try {
            AtomicReference<TunnelInfo> createdTunnelRef = new AtomicReference<>();
            Process process = executor.run(
                new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_CREATE, tunnelInfo.getName()),
                line -> {
                    if (tunnelAlreadyExist(line)) {
                        Constants.LOG.debug("Cannot create tunnel. Tunnel with name {} already exists", tunnelInfo.getName());
                        createdTunnelRef.set(null);
                    }
                    Constants.LOG.debug(line);
                    Matcher m = UUID_REGEX.matcher(line);
                    if (m.find()) {
                        String group = m.group(1);
                        Constants.LOG.info("Created tunnel with ID: {}", group);
                        tunnelInfo.setId(group);
                    }
                    createdTunnelRef.set(tunnelInfo);
                },
                Constants.LOG::error
            );
            if (process != null) {
                process.waitFor();
            }
            if (createdTunnelRef.get() == null) {
                Constants.LOG.info("Getting existing tunnel with name and ID: {}, {}", tunnelInfo.getName(), tunnelInfo.getId());
                TunnelInfo existingTunnel = cfHandler.getTunnelInfo(tunnelInfo);
                if (existingTunnel != null) {
                    Constants.LOG.info("Using existing tunnel, {}, {}", existingTunnel.getName(), existingTunnel.getId());
                    tunnelInfo.setTunnelAndId(existingTunnel);
                }
            } else {
                tunnelInfo.setId(createdTunnelRef.get().getId());
                tunnelInfo.setName(createdTunnelRef.get().getName());
            }
            return tunnelInfo;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Constants.LOG.error(e.getMessage());
            return null;
        }
    }

    public void routeDnsToTunnel(String tunnelName, String routeDnsDomain) {
        executor.run(new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_ROUTE, Constants.CMD_DNS, tunnelName, routeDnsDomain), null, null);
    }

    public synchronized boolean runLocalTunnel(String tunnelIdOrName) {
        return launchTunnelProcess(
            new SubCommand(Constants.CMD_TUNNEL, "--config", "\"" + tunnelConfigLocation + "\"", Constants.CMD_RUN, tunnelIdOrName),
            null
        );
    }

    public void createTunnelConfig(String tunnelId, String localIp, String hostname, int serverPort) {
        String resolvedIp = resolveLocalIp(localIp);
        int resolvedPort = resolveServerPort(serverPort);
        Constants.LOG.debug("Creating tunnel config for tunnelId: {}, localIp: {}, hostname: {}, serverPort: {}",
            tunnelId, resolvedIp, hostname, resolvedPort);
        TunnelConfig config = new TunnelConfig(hostname, "tcp://" + resolvedIp + ":" + resolvedPort, tunnelId);
        Path configPath = getOrCreateTunnelConfig();
        if (configPath == null) {
            return;
        }
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
        return line.toLowerCase().contains("tunnel with name already exist");
    }
}
