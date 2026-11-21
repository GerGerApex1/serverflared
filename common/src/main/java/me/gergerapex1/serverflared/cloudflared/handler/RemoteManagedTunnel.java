package me.gergerapex1.serverflared.cloudflared.handler;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.cloudflared.remote.CloudflareApiClient;
import me.gergerapex1.serverflared.cloudflared.remote.CloudflareApiException;
import me.gergerapex1.serverflared.cloudflared.remote.RemoteTunnelToken;
import me.gergerapex1.serverflared.process.ProcessHandler;
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

    RemoteManagedTunnel(CloudFlaredHandler cloudflaredHandler, TunnelMode mode, ProcessHandler customExecutor) {
        super(cloudflaredHandler, customExecutor);
        if (mode == null || !mode.isRemote()) {
            throw new IllegalArgumentException("RemoteManagedTunnel requires REMOTE_TOKEN or REMOTE_API mode.");
        }
        this.mode = mode;
    }

    protected CloudflareApiClient createApiClient(String apiToken) {
        return new CloudflareApiClient(apiToken);
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
        return prepareTunnel(config, localIp, serverPort, null);
    }

    @Override
    public TunnelInfo prepareTunnel(
        Config config,
        String localIp,
        int serverPort,
        WriteCommitCoordinator writeCoordinator
    ) {
        if (config == null) {
            state = TunnelState.ERROR;
            lastConnectionDetail = "Configuration is null";
            return null;
        }
        if (mode == TunnelMode.REMOTE_TOKEN) {
            return prepareProvidedTokenTunnel(config);
        }
        return prepareApiManagedTunnel(config, localIp, serverPort, writeCoordinator);
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

    private TunnelInfo prepareApiManagedTunnel(
        Config config,
        String localIp,
        int serverPort,
        WriteCommitCoordinator writeCoordinator
    ) {
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
            final CloudflareApiClient api = createApiClient(apiToken);

            String accountId = remote.getAccountId();
            if (accountId == null || accountId.trim().isEmpty()) {
                accountId = api.resolveAccountId();
            }
            final String resolvedAccountId = accountId;

            // Resolve and validate the DNS target zone BEFORE mutating live tunnel ingress.
            String zoneId = null;
            if (remote.isManageDns()) {
                String configuredZoneId = remote.getZoneId();
                if (configuredZoneId == null || configuredZoneId.trim().isEmpty()) {
                    zoneId = api.resolveZoneId(resolvedAccountId, config.getHostName());
                } else {
                    api.verifyZoneBelongsToAccount(configuredZoneId, resolvedAccountId);
                    zoneId = configuredZoneId.trim();
                }
            }
            final String resolvedZoneId = zoneId;

            String tunnelName = config.getTunnelName();
            String configuredId = config.getTunnelId();

            CloudflareApiClient.RemoteTunnelRecord record = api.findTunnel(resolvedAccountId, configuredId, tunnelName);
            final String tunnelId;
            final String resolvedName;
            final String runToken;

            if (record == null) {
                final AtomicReference<CloudflareApiClient.RemoteTunnelRecord> createdRef = new AtomicReference<>();
                final AtomicReference<Exception> createFailure = new AtomicReference<>();
                BooleanSupplier createAction = () -> {
                    try {
                        Constants.LOG.info("Remote tunnel '{}' not found; creating via Cloudflare API...", tunnelName);
                        createdRef.set(api.createRemoteTunnel(resolvedAccountId, tunnelName));
                        return true;
                    } catch (Exception e) {
                        createFailure.set(e);
                        return false;
                    }
                };
                boolean createCommitted = (writeCoordinator != null)
                    ? writeCoordinator.executeIfOwner(createAction)
                    : createAction.getAsBoolean();
                if (!createCommitted) {
                    rethrowIfPresent(createFailure.get());
                    return null;
                }
                CloudflareApiClient.RemoteTunnelRecord created = createdRef.get();
                if (created == null) {
                    return null;
                }
                tunnelId = created.getId();
                resolvedName = (created.getName() != null && !created.getName().isEmpty()) ? created.getName() : tunnelName;
                runToken = (created.getToken() != null && !created.getToken().isEmpty())
                    ? created.getToken()
                    : api.getTunnelToken(resolvedAccountId, tunnelId);
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
                runToken = api.getTunnelToken(resolvedAccountId, tunnelId);
                Constants.LOG.info("Found existing remote tunnel '{}' with ID {}", resolvedName, tunnelId);
            }

            final boolean existingTunnel = (record != null);
            final String serviceUrl = formatTcpServiceUrl(localIp, serverPort);
            final CloudflareApiClient.DnsCnameRecord priorDnsRecord = remote.isManageDns()
                ? api.getDnsCnameRecord(resolvedZoneId, config.getHostName())
                : null;

            final AtomicReference<CloudflareApiClient.IngressUpdateReceipt> receiptRef = new AtomicReference<>();
            final AtomicReference<Exception> ingressFailure = new AtomicReference<>();

            BooleanSupplier ingressWriteAction = () -> {
                try {
                    // Snapshot current ingress immediately before mutating so we merge against the latest state.
                    JsonNode previousConfig = existingTunnel
                        ? api.getTunnelConfiguration(resolvedAccountId, tunnelId)
                        : null;

                    CloudflareApiClient.IngressUpdateReceipt applied = api.updateTunnelIngress(
                        resolvedAccountId, tunnelId, config.getHostName(), serviceUrl, previousConfig
                    );
                    receiptRef.set(applied);
                    Constants.LOG.info("Synced remote tunnel ingress: {} -> {}", config.getHostName(), serviceUrl);
                    return true;
                } catch (Exception e) {
                    ingressFailure.set(e);
                    return false;
                }
            };

            boolean ingressCommitted = (writeCoordinator != null)
                ? writeCoordinator.executeIfOwner(ingressWriteAction)
                : ingressWriteAction.getAsBoolean();

            final CloudflareApiClient.IngressUpdateReceipt receipt = receiptRef.get();
            if (!ingressCommitted) {
                rethrowIfPresent(ingressFailure.get());
                // Distinguish rejection before ingress PUT (receipt == null) from cancellation after PUT succeeded.
                if (receipt != null) {
                    Constants.LOG.warn(
                        "Tunnel preparation canceled after ingress update for '{}'; reconciling applied ingress rule.",
                        config.getHostName()
                    );
                    rollbackIngressSafely(api, resolvedAccountId, tunnelId, config.getHostName(), receipt);
                }
                return null;
            }

            if (remote.isManageDns()) {
                final AtomicBoolean dnsWriteAttempted = new AtomicBoolean(false);
                final AtomicReference<Exception> dnsFailure = new AtomicReference<>();

                BooleanSupplier dnsWriteAction = () -> {
                    dnsWriteAttempted.set(true);
                    try {
                        api.syncDnsCname(resolvedZoneId, config.getHostName(), tunnelId);
                        return true;
                    } catch (CloudflareApiException | RuntimeException dnsError) {
                        dnsFailure.set(dnsError);
                        return false;
                    }
                };

                boolean dnsCommitted = (writeCoordinator != null)
                    ? writeCoordinator.executeIfOwner(dnsWriteAction)
                    : dnsWriteAction.getAsBoolean();

                if (!dnsCommitted) {
                    Exception dnsError = dnsFailure.get();
                    if (dnsError != null) {
                        DnsFailureResolution resolution = handleDnsSyncFailure(
                            api,
                            resolvedAccountId,
                            resolvedZoneId,
                            tunnelId,
                            config.getHostName(),
                            receipt,
                            priorDnsRecord,
                            dnsError,
                            writeCoordinator
                        );
                        if (resolution == DnsFailureResolution.CANCELED_AND_RECONCILED) {
                            return null;
                        }
                        if (resolution == DnsFailureResolution.FAILED) {
                            rethrowIfPresent(dnsError);
                        }
                        // CONFIRMED_APPLIED_CONTINUE: DNS now points to new tunnel and ownership is still valid.
                    } else if (!dnsWriteAttempted.get()) {
                        // Ownership lost before DNS sync; DNS was not mutated, so roll back ingress for consistency.
                        Constants.LOG.warn(
                            "Tunnel preparation canceled before DNS sync for '{}'; rolling back applied ingress rule.",
                            config.getHostName()
                        );
                        rollbackIngressSafely(api, resolvedAccountId, tunnelId, config.getHostName(), receipt);
                        return null;
                    } else {
                        // Ownership lost during/after DNS sync succeeded; restore DNS first, then roll back ingress.
                        Constants.LOG.warn(
                            "Tunnel preparation canceled after DNS sync for '{}'; reconciling DNS and ingress.",
                            config.getHostName()
                        );
                        reconcileDnsAndIngressSafely(
                            api, resolvedAccountId, resolvedZoneId, tunnelId, config.getHostName(), priorDnsRecord, receipt
                        );
                        return null;
                    }
                }
            }

            if (writeCoordinator != null && receipt != null) {
                final AtomicBoolean rolledBack = new AtomicBoolean(false);
                final boolean dnsManaged = remote.isManageDns();
                writeCoordinator.onAppliedWrite(() -> {
                    if (!rolledBack.compareAndSet(false, true)) {
                        return;
                    }
                    if (dnsManaged) {
                        reconcileDnsAndIngressSafely(
                            api, resolvedAccountId, resolvedZoneId, tunnelId, config.getHostName(), priorDnsRecord, receipt
                        );
                    } else {
                        rollbackIngressSafely(api, resolvedAccountId, tunnelId, config.getHostName(), receipt);
                    }
                });
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

    private enum DnsFailureResolution {
        CONFIRMED_APPLIED_CONTINUE,
        CANCELED_AND_RECONCILED,
        FAILED
    }

    private static DnsFailureResolution handleDnsSyncFailure(
        CloudflareApiClient api,
        String accountId,
        String zoneId,
        String tunnelId,
        String hostName,
        CloudflareApiClient.IngressUpdateReceipt receipt,
        CloudflareApiClient.DnsCnameRecord priorDnsRecord,
        Exception dnsError,
        WriteCommitCoordinator writeCoordinator
    ) {
        if (!isAmbiguousDnsFailure(dnsError)) {
            Constants.LOG.error(
                "DNS sync rejected after ingress update for '{}'; attempting selective ingress rollback.",
                hostName
            );
            rollbackIngressSafely(api, accountId, tunnelId, hostName, receipt);
            return DnsFailureResolution.FAILED;
        }

        Constants.LOG.warn(
            "Ambiguous DNS sync failure for '{}'; re-reading live DNS record before deciding whether to roll back ingress.",
            hostName
        );
        CloudflareApiClient.DnsCnameRecord liveDnsRecord;
        try {
            liveDnsRecord = api.getDnsCnameRecord(zoneId, hostName);
        } catch (Exception readError) {
            Constants.LOG.error(
                "Could not verify live DNS record for '{}' after ambiguous DNS failure ({}); leaving ingress intact to avoid breaking live DNS.",
                hostName, readError.getMessage()
            );
            return DnsFailureResolution.FAILED;
        }

        if (liveDnsRecord != null && liveDnsRecord.matchesSyncedTarget(tunnelId)) {
            if (writeCoordinator == null || writeCoordinator.isStillOwner()) {
                Constants.LOG.info(
                    "DNS sync for '{}' returned an ambiguous error, but re-read confirmed CNAME already points to tunnel {}; continuing.",
                    hostName, tunnelId
                );
                return DnsFailureResolution.CONFIRMED_APPLIED_CONTINUE;
            }
            Constants.LOG.warn(
                "Re-read confirmed DNS for '{}' points to tunnel {}, but preparation was canceled; restoring prior DNS before rolling back ingress.",
                hostName, tunnelId
            );
            reconcileDnsAndIngressSafely(api, accountId, zoneId, tunnelId, hostName, priorDnsRecord, receipt);
            return DnsFailureResolution.CANCELED_AND_RECONCILED;
        }

        if (liveDnsRecord != null && liveDnsRecord.matchesTunnelTarget(tunnelId)) {
            // Live DNS points to the new tunnel (e.g., unproxied); restore prior DNS before touching ingress.
            boolean priorAlreadyPointedToTunnel = (priorDnsRecord != null && priorDnsRecord.matchesTunnelTarget(tunnelId));
            if (priorAlreadyPointedToTunnel) {
                rollbackIngressSafely(api, accountId, tunnelId, hostName, receipt);
            } else {
                try {
                    api.restoreDnsCnameRecord(zoneId, hostName, tunnelId, priorDnsRecord, liveDnsRecord);
                    rollbackIngressSafely(api, accountId, tunnelId, hostName, receipt);
                } catch (Exception restoreError) {
                    Constants.LOG.error(
                        "Failed to restore prior DNS target for '{}' after ambiguous DNS failure ({}); leaving ingress intact.",
                        hostName, restoreError.getMessage()
                    );
                }
            }
            return DnsFailureResolution.FAILED;
        }

        // Re-read confirmed DNS does NOT point to the new tunnel; safe to roll back ingress.
        Constants.LOG.error(
            "DNS sync did not update CNAME for '{}' to tunnel {}; rolling back ingress rule.",
            hostName, tunnelId
        );
        rollbackIngressSafely(api, accountId, tunnelId, hostName, receipt);
        return DnsFailureResolution.FAILED;
    }

    private static boolean isAmbiguousDnsFailure(Exception error) {
        if (!(error instanceof CloudflareApiException)) {
            return true;
        }
        int status = ((CloudflareApiException) error).getHttpStatus();
        // Explicit 4xx rejections (except 408 Request Timeout) confirm Cloudflare rejected the DNS mutation.
        return status < 400 || status >= 500 || status == 408;
    }

    private static void reconcileDnsAndIngressSafely(
        CloudflareApiClient api,
        String accountId,
        String zoneId,
        String tunnelId,
        String hostName,
        CloudflareApiClient.DnsCnameRecord priorDnsRecord,
        CloudflareApiClient.IngressUpdateReceipt receipt
    ) {
        boolean dnsChangedByUs = (priorDnsRecord == null || !priorDnsRecord.matchesSyncedTarget(tunnelId));
        if (dnsChangedByUs) {
            try {
                CloudflareApiClient.DnsCnameRecord liveDns = api.getDnsCnameRecord(zoneId, hostName);
                if (liveDns != null && liveDns.matchesTunnelTarget(tunnelId)) {
                    api.restoreDnsCnameRecord(zoneId, hostName, tunnelId, priorDnsRecord, liveDns);
                }
            } catch (Exception dnsRestoreError) {
                Constants.LOG.error(
                    "Could not restore prior DNS CNAME for '{}' ({}); leaving ingress intact so live DNS is not broken.",
                    hostName, dnsRestoreError.getMessage()
                );
                return;
            }
        }
        rollbackIngressSafely(api, accountId, tunnelId, hostName, receipt);
    }

    private static void rollbackIngressSafely(
        CloudflareApiClient api,
        String accountId,
        String tunnelId,
        String hostName,
        CloudflareApiClient.IngressUpdateReceipt receipt
    ) {
        if (receipt == null) {
            return;
        }
        try {
            api.rollbackTunnelIngress(accountId, tunnelId, receipt);
            Constants.LOG.info("Selectively rolled back ingress rule for '{}'.", hostName);
        } catch (Exception rollbackError) {
            Constants.LOG.error(
                "Could not safely roll back ingress rule for '{}' (leaving newer configuration intact): {}",
                hostName, rollbackError.getMessage()
            );
        }
    }

    private static void rethrowIfPresent(Exception failure) throws Exception {
        if (failure instanceof CloudflareApiException) {
            throw (CloudflareApiException) failure;
        }
        if (failure != null) {
            throw failure;
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
