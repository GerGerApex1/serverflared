package me.gergerapex1.serverflared.utils.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.UUID;
import me.gergerapex1.serverflared.utils.config.annonations.Comment;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Config{
	private int version = 3;
    @Comment("The ID of the Cloudflare tunnel.")
    private String tunnelId = new UUID(0, 0).toString();
    @Comment("The name of the Cloudflare tunnel.")
    private String tunnelName = "serverflared-tunnel";
	@JsonAlias("subdomain")
	@Comment("The full hostname for your Cloudflare tunnel (e.g., \"minecraft.yourdomain.net\" or \"yourdomain.net\"). "
			+ "Your domain must be valid & authenticated in your Cloudflare account")
    private String hostName = "minecraftsubdomain.yourdomain.net";
	@JsonAlias({"permissionMode"})
	@Comment("Controls who can execute /serverflared commands. Options: \"OP_OR_PERMISSION\" (Server Console, OP Level 4, or serverflared.admin permission)"
			+ " or \"CONSOLE_ONLY\" (Dedicated Server Console only). IT IS RECOMMENDED TO BE SET AT \"CONSOLE_ONLY\"")
	private String commandPermissionMode = "CONSOLE_ONLY";

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

	public String getCommandPermissionMode() {
		return commandPermissionMode;
	}

	public void setCommandPermissionMode(String commandPermissionMode) {
		if (commandPermissionMode == null || commandPermissionMode.trim().isEmpty()) {
			this.commandPermissionMode = "OP_OR_PERMISSION";
		} else {
			this.commandPermissionMode = commandPermissionMode.trim();
		}
	}

	@JsonSetter("consoleOnlyCommands")
	public void setConsoleOnlyCommands(boolean consoleOnlyCommands) {
		this.commandPermissionMode = consoleOnlyCommands ? "CONSOLE_ONLY" : "OP_OR_PERMISSION";
	}

	@JsonIgnore
	public boolean isConsoleOnly() {
		String mode = commandPermissionMode.trim();
		boolean permissive = "OP_OR_PERMISSION".equalsIgnoreCase(mode) || "false".equalsIgnoreCase(mode);
		return !permissive;
	}
}
