package me.gergerapex1.serverflared.cloudflared.handler;

import java.util.function.BooleanSupplier;
import me.gergerapex1.serverflared.utils.config.Config;

public interface ManagedTunnel {
    @FunctionalInterface
    interface WriteCommitCoordinator {
        boolean executeIfOwner(BooleanSupplier writeAction);

        default boolean isStillOwner() {
            return executeIfOwner(() -> true);
        }

        default void onAppliedWrite(Runnable rollbackAction) {
            // Optional hook for coordinators that reconcile applied writes if canceled before publication.
        }
    }

    TunnelMode getMode();

    boolean requiresCliAuth();

    TunnelInfo prepareTunnel(Config config, String localIp, int serverPort);

    default TunnelInfo prepareTunnel(
        Config config,
        String localIp,
        int serverPort,
        WriteCommitCoordinator writeCoordinator
    ) {
        if (writeCoordinator == null) {
            return prepareTunnel(config, localIp, serverPort);
        }
        final TunnelInfo[] holder = new TunnelInfo[1];
        boolean committed = writeCoordinator.executeIfOwner(() -> {
            holder[0] = prepareTunnel(config, localIp, serverPort);
            return holder[0] != null;
        });
        return committed ? holder[0] : null;
    }

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
