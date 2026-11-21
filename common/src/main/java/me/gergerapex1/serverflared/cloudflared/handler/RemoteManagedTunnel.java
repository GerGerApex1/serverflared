package me.gergerapex1.serverflared.cloudflared.handler;

import java.util.Collections;
import java.util.Map;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.cloudflared.remote.CloudflareApiClient;
import me.gergerapex1.serverflared.cloudflared.remote.CloudflareApiException;
import me.gergerapex1.serverflared.cloudflared.remote.RemoteTunnelToken;
import me.gergerapex1.serverflared.process.SubCommand;
import me.gergerapex1.serverflared.utils.config.Config;
import me.gergerapex1.serverflared.utils.config.RemoteConfig;

/**
 * Manages remotely-managed Cloudflare Tunnels in either:
 * - REMOTE_TOKEN mode: uses a user-provided tunnel token from the Cloudflare Zero Trust Dashboard.
 * - REMOTE_API mode: creates/configures the remote tunnel, ingress, and DNS via Cloudflare REST API v4.
 */
public class RemoteManagedTunnel extends AbstractManagedTunnel {
    public static final String ENV_CLOUDFLARED_TUNNEL_TOKEN = "TUNNEL_TOKEN";

    private final TunnelMode mode;
    private volatile String activeRunToken;

    public RemoteManagedTunnel(CloudFlaredHandler cloudflaredHandler, TunnelMode mode) {
        super(cloudflaredHandler);
        if (mode == null || !mode.isRemote()) {
            throw new IllegalArgumentException("RemoteManagedTunnel requires REMOTE_TOKEN or REMOTE_API mode.");
        }
        this.mode = mode;
    }

    @Override
    public TunnelMode getMode() {
        return mode;
    }

    @Override
    public boolean requiresCliAuth() {
        return false;
    }

    @Override
    public TunnelInfo prepareTunnel(Config config, String localIp, int serverPort) {
        if (config == null) {
            state = TunnelState.ERROR;
            lastConnectionDetail = "Configuration is null";
            return null;
        }
        if (mode == TunnelMode.REMOTE_TOKEN) {
            return prepareProvidedTokenTunnel(config);
        }
        return prepareApiManagedTunnel(config, localIp, serverPort);
    }

    private TunnelInfo prepareProvidedTokenTunnel(Config config) {
        RemoteConfig remote = config.getRemote();
        String rawToken = remote.resolveTunnelToken();
        if (rawToken.isEmpty()) {
            state = TunnelState.ERROR;
            lastConnectionDetail = "Missing remote.tunnelToken (or " + RemoteConfig.ENV_TUNNEL_TOKEN + " env var)";
            Constants.LOG.error("REMOTE_TOKEN mode requires remote.tunnelToken in config.yml or {} env var.",
                RemoteConfig.ENV_TUNNEL_TOKEN);
            return null;
        }

        try {
            RemoteTunnelToken parsed = RemoteTunnelToken.parseAndValidate(rawToken);
            this.activeRunToken = parsed.getRawToken();

            TunnelInfo info = new TunnelInfo();
            info.setId(parsed.getTunnelId());
            info.setName(config.getTunnelName());
            Constants.LOG.info("Validated remote tunnel token for tunnel ID {}", parsed.getTunnelId());
            return info;
        } catch (IllegalArgumentException e) {
            state = TunnelState.ERROR;
            lastConnectionDetail = "Invalid tunnel token: " + e.getMessage();
            Constants.LOG.error("Invalid remote tunnel token: {}", e.getMessage());
            return null;
        }
    }

    private TunnelInfo prepareApiManagedTunnel(Config config, String localIp, int serverPort) {
        RemoteConfig remote = config.getRemote();
        String apiToken = remote.resolveApiToken();
        if (apiToken.isEmpty()) {
            state = TunnelState.ERROR;
            lastConnectionDetail = "Missing remote.apiToken (or " + RemoteConfig.ENV_API_TOKEN + " env var)";
            Constants.LOG.error("REMOTE_API mode requires remote.apiToken in config.yml or {} env var.",
                RemoteConfig.ENV_API_TOKEN);
            return null;
        }

        try {
            CloudflareApiClient api = new CloudflareApiClient(apiToken);

            String accountId = remote.getAccountId();
            if (accountId == null || accountId.trim().isEmpty()) {
                accountId = api.resolveAccountId();
            }

            String tunnelName = config.getTunnelName();
            String configuredId = config.getTunnelId();

            CloudflareApiClient.RemoteTunnelRecord record = api.findTunnel(accountId, configuredId, tunnelName);
            String tunnelId;
            String resolvedName;
            String runToken;

            if (record == null) {
                Constants.LOG.info("Remote tunnel '{}' not found; creating via Cloudflare API...", tunnelName);
                CloudflareApiClient.RemoteTunnelRecord created = api.createRemoteTunnel(accountId, tunnelName);
                tunnelId = created.getId();
                resolvedName = (created.getName() != null && !created.getName().isEmpty()) ? created.getName() : tunnelName;
                runToken = (created.getToken() != null && !created.getToken().isEmpty())
                    ? created.getToken()
                    : api.getTunnelToken(accountId, tunnelId);
                Constants.LOG.info("Created remote tunnel '{}' with ID {}", resolvedName, tunnelId);
            } else {
                if (!record.isRemoteConfig()) {
                    state = TunnelState.ERROR;
                    lastConnectionDetail = "Existing tunnel '" + record.getName() + "' is locally-managed (remote_config=false)";
                    Constants.LOG.error(
                        "Tunnel '{}' ({}) already exists as a locally-managed tunnel. "
                            + "Use tunnelMode: LOCAL or choose a different tunnelName for REMOTE_API mode.",
                        record.getName(), record.getId()
                    );
                    return null;
                }
                tunnelId = record.getId();
                resolvedName = (record.getName() != null && !record.getName().isEmpty()) ? record.getName() : tunnelName;
                runToken = api.getTunnelToken(accountId, tunnelId);
                Constants.LOG.info("Found existing remote tunnel '{}' with ID {}", resolvedName, tunnelId);
            }

            String resolvedIp = resolveLocalIp(localIp);
            int resolvedPort = resolveServerPort(serverPort);
            String serviceUrl = "tcp://" + resolvedIp + ":" + resolvedPort;
            api.updateTunnelIngress(accountId, tunnelId, config.getHostName(), serviceUrl);
            Constants.LOG.info("Synced remote tunnel ingress: {} -> {}", config.getHostName(), serviceUrl);

            if (remote.isManageDns()) {
                String zoneId = remote.getZoneId();
                if (zoneId == null || zoneId.trim().isEmpty()) {
                    zoneId = api.resolveZoneId(config.getHostName());
                }
                api.syncDnsCname(zoneId, config.getHostName(), tunnelId);
            }

            this.activeRunToken = runToken;

            TunnelInfo info = new TunnelInfo();
            info.setId(tunnelId);
            info.setName(resolvedName);
            return info;
        } catch (CloudflareApiException e) {
            state = TunnelState.ERROR;
            lastConnectionDetail = "Cloudflare API error: " + e.getMessage();
            Constants.LOG.error("Failed to prepare REMOTE_API tunnel: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            state = TunnelState.ERROR;
            lastConnectionDetail = "Unexpected error preparing remote tunnel: " + e.getMessage();
            Constants.LOG.error("Unexpected error preparing REMOTE_API tunnel", e);
            return null;
        }
    }

    @Override
    public boolean startTunnel(Config config, TunnelInfo info) {
        String token = this.activeRunToken;
        if (token == null || token.isEmpty()) {
            state = TunnelState.ERROR;
            lastConnectionDetail = "No tunnel run token available. Check configuration and logs.";
            Constants.LOG.error("Cannot start remote tunnel: run token was not prepared.");
            return false;
        }
        Map<String, String> env = Collections.singletonMap(ENV_CLOUDFLARED_TUNNEL_TOKEN, token);
        return launchTunnelProcess(new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_RUN), env);
    }

    @Override
    public synchronized void terminateProcesses() {
        super.terminateProcesses();
    }
}
