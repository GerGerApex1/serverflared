package me.gergerapex1.serverflared.utils.config;

import java.util.UUID;

public class Config{
    private String tunnelId = new UUID(0, 0).toString();
    private String tunnelName = "servergotflared-tunnel";
    private String hostname = "subdomain.example.com";
    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getTunnelId() {
        return tunnelId;
    }

    public void setTunnelId(String tunnelId) {
        this.tunnelId = tunnelId;
    }

    public String getTunnelName() {
        return tunnelName;
    }

    public void setTunnelName(String tunnelName) {
        this.tunnelName = tunnelName;
    }

}
