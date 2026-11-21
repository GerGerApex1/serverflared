package me.gergerapex1.serverflared.utils.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import me.gergerapex1.serverflared.utils.config.annonations.Comment;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteConfig {
    public static final String ENV_TUNNEL_TOKEN = "SERVERFLARED_TUNNEL_TOKEN";
    public static final String ENV_API_TOKEN = "SERVERFLARED_API_TOKEN";

    @Comment("Required for REMOTE_TOKEN mode (can also be set via SERVERFLARED_TUNNEL_TOKEN env var)")
    private String tunnelToken = "";

    @Comment("Required for REMOTE_API mode (can also be set via SERVERFLARED_API_TOKEN env var). "
        + "Needs Account:Cloudflare Tunnel:Edit and Zone:DNS:Edit permissions.")
    private String apiToken = "";

    @Comment("Optional for REMOTE_API mode; auto-discovered if your token has access to a single account")
    private String accountId = "";

    @Comment("Optional for REMOTE_API mode; auto-discovered from hostName if omitted")
    private String zoneId = "";

    @Comment("Whether REMOTE_API mode should automatically create/update the DNS CNAME record")
    private boolean manageDns = true;

    public String getTunnelToken() {
        return tunnelToken;
    }

    public void setTunnelToken(String tunnelToken) {
        this.tunnelToken = tunnelToken != null ? tunnelToken.trim() : "";
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken != null ? apiToken.trim() : "";
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId != null ? accountId.trim() : "";
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId != null ? zoneId.trim() : "";
    }

    public boolean isManageDns() {
        return manageDns;
    }

    public void setManageDns(boolean manageDns) {
        this.manageDns = manageDns;
    }

    @JsonIgnore
    public String resolveTunnelToken() {
        if (tunnelToken != null && !tunnelToken.trim().isEmpty()) {
            return tunnelToken.trim();
        }
        String env = System.getenv(ENV_TUNNEL_TOKEN);
        return env != null ? env.trim() : "";
    }

    @JsonIgnore
    public String resolveApiToken() {
        if (apiToken != null && !apiToken.trim().isEmpty()) {
            return apiToken.trim();
        }
        String env = System.getenv(ENV_API_TOKEN);
        return env != null ? env.trim() : "";
    }
}
