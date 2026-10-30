package me.gergerapex1.serverflared.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import me.gergerapex1.serverflared.ServerFlared;
import me.gergerapex1.serverflared.cloudflared.handler.CloudFlaredHandler;
import me.gergerapex1.serverflared.cloudflared.handler.LocalManagedTunnel;
import me.gergerapex1.serverflared.cloudflared.handler.TunnelInfo;
import me.gergerapex1.serverflared.utils.config.Config;
import me.gergerapex1.serverflared.utils.config.ConfigManager;

public final class ServerFlaredCommand {
    public static final String COMMAND_NAME = "serverflared";
    public static final String COMMAND_ALIAS = "sf";
    public static final String PERMISSION_NODE = "serverflared.admin";
    public static final int REQUIRED_OP_LEVEL = 4;

    public static final String ConsoleOnlyDenialMessage = "This command can only be executed from the server console.";
    public static final String PermissionDenialMessage = "You do not have permission to execute this command.";

    public static final List<String> SUBCOMMANDS = Collections.unmodifiableList(
        Arrays.asList("status", "reload", "start", "stop", "login", "clientcmd")
    );

    private ServerFlaredCommand() {
    }

    /**
     * Centralized authorization check honoring config.yml commandPermissionMode.
     */
    public static boolean isAuthorized(CommandSenderAdapter sender) {
        if (sender == null) {
            return false;
        }
        if (ServerFlared.isConsoleOnlyMode()) {
            return sender.isConsole();
        }
        return sender.isConsole() || sender.hasPermission(PERMISSION_NODE, REQUIRED_OP_LEVEL);
    }

    public static boolean execute(CommandSenderAdapter sender, String[] args) {
        if (sender == null) {
            return false;
        }
        if (!isAuthorized(sender)) {
            if (ServerFlared.isConsoleOnlyMode() && !sender.isConsole()) {
                sender.sendMessage("\u00A7c" + ConsoleOnlyDenialMessage);
            } else {
                sender.sendMessage("\u00A7c" + PermissionDenialMessage);
            }
            return true;
        }

        if (args == null || args.length == 0 || args[0] == null || args[0].trim().isEmpty()) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].trim().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status":
                handleStatus(sender);
                break;
            case "reload":
                ServerFlared.reloadAsync(sender);
                break;
            case "start":
                ServerFlared.startTunnelAsync(sender);
                break;
            case "stop":
                ServerFlared.stopTunnelAsync(sender);
                break;
            case "login":
                ServerFlared.loginAsync(sender);
                break;
            case "clientcmd":
                handleClientCmd(sender);
                break;
            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    public static List<String> suggest(CommandSenderAdapter sender, String[] args) {
        if (!isAuthorized(sender)) {
            return Collections.emptyList();
        }
        if (args == null || args.length == 0) {
            return new ArrayList<>(SUBCOMMANDS);
        }
        if (args.length == 1) {
            String prefix = args[0] != null ? args[0].toLowerCase(Locale.ROOT) : "";
            List<String> matches = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    matches.add(sub);
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }

    private static void handleStatus(CommandSenderAdapter sender) {
        LocalManagedTunnel localHandler = ServerFlared.getLocalHandler();
        CloudFlaredHandler cfHandler = ServerFlared.getHandler();
        ConfigManager cfgManager = ServerFlared.getConfigManager();
        Config cfg = cfgManager != null ? cfgManager.CONFIG : null;
        TunnelInfo tunnelInfo = ServerFlared.getTunnelInfo();

        LocalManagedTunnel.TunnelState state = localHandler != null
            ? localHandler.getState()
            : LocalManagedTunnel.TunnelState.STOPPED;

        long pid = localHandler != null ? localHandler.getActivePid() : -1L;
        long uptimeMillis = localHandler != null ? localHandler.getUptimeMillis() : 0L;
        String connectionDetail = localHandler != null ? localHandler.getLastConnectionDetail() : "Not initialized";
        boolean authenticated = cfHandler != null && cfHandler.isAuthenticated();

        String tunnelName = (tunnelInfo != null && tunnelInfo.getName() != null && !tunnelInfo.getName().isEmpty())
            ? tunnelInfo.getName()
            : (cfg != null ? cfg.getTunnelName() : "N/A");
        String tunnelId = (tunnelInfo != null && tunnelInfo.getId() != null && !tunnelInfo.getId().isEmpty())
            ? tunnelInfo.getId()
            : (cfg != null ? cfg.getTunnelId() : "N/A");
        String hostName = cfg != null ? cfg.getHostName() : "N/A";
        String permMode = cfg != null ? cfg.getCommandPermissionMode() : "OP_OR_PERMISSION";

        String stateColor;
        switch (state) {
            case RUNNING:
                stateColor = "\u00A7a";
                break;
            case STARTING:
                stateColor = "\u00A7e";
                break;
            case ERROR:
                stateColor = "\u00A7c";
                break;
            case STOPPED:
            default:
                stateColor = "\u00A77";
                break;
        }

        sender.sendMessage("\u00A78\u00A7m----------------\u00A7r \u00A76\u00A7lServerFlared Status \u00A78\u00A7m----------------");
        sender.sendMessage("\u00A77State: " + stateColor + state.name());
        sender.sendMessage("\u00A77Authenticated: " + (authenticated ? "\u00A7aYes" : "\u00A7cNo"));
        sender.sendMessage("\u00A77PID: \u00A7f" + (pid > 0 ? String.valueOf(pid) : "N/A"));
        sender.sendMessage("\u00A77Uptime: \u00A7f" + formatDuration(uptimeMillis));
        sender.sendMessage("\u00A77Tunnel Name: \u00A7f" + tunnelName + " \u00A78(" + tunnelId + ")");
        sender.sendMessage("\u00A77Hostname: \u00A7b" + hostName);
        sender.sendMessage("\u00A77Permission Mode: \u00A7f" + permMode);
        sender.sendMessage("\u00A77Connection Details: \u00A7f" + connectionDetail);
    }

    private static void handleClientCmd(CommandSenderAdapter sender) {
        ConfigManager cfgManager = ServerFlared.getConfigManager();
        String hostName = (cfgManager != null && cfgManager.CONFIG != null && cfgManager.CONFIG.getHostName() != null)
            ? cfgManager.CONFIG.getHostName().trim()
            : "minecraftsubdomain.yourdomain.net";

        String command = buildClientCommand(hostName);
        if (sender.isConsole()) {
            sender.sendMessage("[ServerFlared] Client connection command: " + command);
        } else {
            sender.sendCopyableText(
                "\u00A76[ServerFlared] \u00A7eClick to copy client command: \u00A7b\u00A7n" + command,
                command
            );
        }
    }

    public static String buildClientCommand(String hostName) {
        return "cloudflared access tcp --hostname " + hostName + " --url localhost:25565";
    }

    private static void sendUsage(CommandSenderAdapter sender) {
        sender.sendMessage("\u00A76[ServerFlared] \u00A77Usage: \u00A7e/serverflared <status|reload|start|stop|login|clientcmd>");
    }

    public static String formatDuration(long millis) {
        if (millis <= 0L) {
            return "N/A";
        }
        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");
        return sb.toString();
    }
}
