package me.gergerapex1.serverflared.cloudflared.handler;

import java.util.Locale;

public enum TunnelMode {
    LOCAL,
    REMOTE_TOKEN,
    REMOTE_API;

    public static TunnelMode fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LOCAL;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (TunnelMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        return LOCAL;
    }

    public static boolean isValid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (TunnelMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public boolean isRemote() {
        return this == REMOTE_TOKEN || this == REMOTE_API;
    }
}
