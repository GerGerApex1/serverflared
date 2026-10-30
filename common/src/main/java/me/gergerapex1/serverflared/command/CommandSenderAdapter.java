package me.gergerapex1.serverflared.command;

public interface CommandSenderAdapter {
    boolean isConsole();

    boolean hasPermission(String node, int opLevel);

    void sendMessage(String message);

    void sendClickableUrl(String text, String url);

    void sendCopyableText(String text, String copyValue);
}
