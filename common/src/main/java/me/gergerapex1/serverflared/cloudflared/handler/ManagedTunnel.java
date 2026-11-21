package me.gergerapex1.serverflared.cloudflared.handler;

import me.gergerapex1.serverflared.utils.config.Config;

public interface ManagedTunnel {
    TunnelMode getMode();

    boolean requiresCliAuth();

    TunnelInfo prepareTunnel(Config config, String localIp, int serverPort);

    boolean startTunnel(Config config, TunnelInfo info);

    void terminateProcesses();

    boolean isTunnelRunning();

    TunnelState getState();

    void setState(TunnelState state);

    String getLastConnectionDetail();

    void setLastConnectionDetail(String detail);

    long getActivePid();

    long getUptimeMillis();
}
