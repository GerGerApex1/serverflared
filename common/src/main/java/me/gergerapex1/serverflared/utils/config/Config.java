package me.gergerapex1.serverflared.utils.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.UUID;
import me.gergerapex1.serverflared.utils.config.annonations.Comment;

public class Config{
	private int version = 2;
    @Comment("The ID of the Cloudflare tunnel.")
    private String tunnelId = new UUID(0, 0).toString();
    @Comment("The name of the Cloudflare tunnel.")
    private String tunnelName = "serverflared-tunnel";
	@JsonAlias("subdomain")
	@Comment("The full hostname for your Cloudflare tunnel (e.g., \"minecraft.yourdomain.net\" or \"yourdomain.net\"). "
			+ "Your domain must be valid & authenticated in your Cloudflare account")
    private String hostName = "minecraftsubdomain.yourdomain.net";

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
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

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}
}
