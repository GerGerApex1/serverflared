package me.gergerapex1.serverflared.utils.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.Locale;
import java.util.UUID;
import me.gergerapex1.serverflared.cloudflared.handler.TunnelMode;
import me.gergerapex1.serverflared.utils.config.annonations.Comment;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {
    public static final int CURRENT_VERSION = 5;

    private int version = CURRENT_VERSION;

    @Comment("Tunnel management mode: \"LOCAL\" (uses cloudflared login cert.pem), "
        + "\"REMOTE_TOKEN\" (uses a pre-created Cloudflare Dashboard tunnel token), "
        + "or \"REMOTE_API\" (automatically creates and manages a remote tunnel via Cloudflare API v4)")
    private String tunnelMode = TunnelMode.LOCAL.name();

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

    @Comment("Settings for REMOTE_TOKEN and REMOTE_API tunnel modes.")
    private RemoteConfig remote = new RemoteConfig();

    public String getTunnelMode() {
        return tunnelMode;
    }

    public void setTunnelMode(String tunnelMode) {
        if (tunnelMode == null || tunnelMode.trim().isEmpty()) {
            this.tunnelMode = TunnelMode.LOCAL.name();
        } else {
            this.tunnelMode = tunnelMode.trim().toUpperCase(Locale.ROOT);
        }
    }

    @JsonIgnore
    public TunnelMode getResolvedTunnelMode() {
        return TunnelMode.fromString(tunnelMode);
    }

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
        String mode = commandPermissionMode != null ? commandPermissionMode.trim() : "";
        boolean permissive = "OP_OR_PERMISSION".equalsIgnoreCase(mode) || "false".equalsIgnoreCase(mode);
        return !permissive;
    }

    public RemoteConfig getRemote() {
        if (remote == null) {
            remote = new RemoteConfig();
        }
        return remote;
    }

    public void setRemote(RemoteConfig remote) {
        this.remote = remote != null ? remote : new RemoteConfig();
    }
}
