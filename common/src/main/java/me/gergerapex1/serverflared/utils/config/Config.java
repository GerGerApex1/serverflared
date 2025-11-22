package me.gergerapex1.serverflared.utils.config;

import java.util.UUID;
import me.gergerapex1.serverflared.utils.config.annonations.Comment;

public class Config{
    @Comment("The ID of the Cloudflare tunnel.")
    private String tunnelId = new UUID(0, 0).toString();
    @Comment("The name of the Cloudflare tunnel.")
    private String tunnelName = "serverflared-tunnel";
    @Comment("The subdomain to use from the domain. Example, \"subdomain\" for will use subdomain.yourdomain.com if you authenticated the domain yourdomain.com.")
    private String subdomain = "subdomain.example.com";
    
    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
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
