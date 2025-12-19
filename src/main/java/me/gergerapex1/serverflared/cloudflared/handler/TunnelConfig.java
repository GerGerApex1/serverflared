package me.gergerapex1.serverflared.cloudflared.handler;

import com.fasterxml.jackson.annotation.JsonProperty;
import me.gergerapex1.serverflared.Constants;

import java.nio.file.Paths;

public class TunnelConfig {
    @JsonProperty("url")
    private String url;
    
    @JsonProperty("tunnel")
    private String tunnelUuid;
    
    @JsonProperty("credentials-file")
    private String credentialsFile;

    public TunnelConfig(String url, String tunnelId) {
        this.url = url;
        this.tunnelUuid = tunnelId;
        this.credentialsFile = buildCredentialsFilePath(tunnelId);
    }
    
    private static String buildCredentialsFilePath(String tunnelId) {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, Constants.CLOUDFLARED_DIR_NAME, tunnelId + ".json").toString();
    }

    public String getTunnelUuid() {
        return tunnelUuid;
    }

    public void setTunnelUuid(String tunnelUuid) {
        this.tunnelUuid = tunnelUuid;
    }

    public String getCredentialsFile() {
        return credentialsFile;
    }

    public void setCredentialsFile(String credentialsFile) {
        this.credentialsFile = credentialsFile;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
