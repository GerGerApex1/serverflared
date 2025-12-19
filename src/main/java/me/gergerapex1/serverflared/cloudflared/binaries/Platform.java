package me.gergerapex1.serverflared.cloudflared.binaries;

import java.util.Locale;

public enum Platform {
    //386 = x86
    LINUX_AMD64("linux", "amd64"),
    LINUX_X86("linux", "386"),
    LINUX_ARM("linux", "arm"),
    LINUX_ARM64("linux", "arm64"),
    WINDOWS_AMD64("windows", "amd64"),
    WINDOWS_X86("windows", "386"),
    UNKNOWN("unknown","unknown");

    public String getOs() {
        return os;
    }

    public String getArch() {
        return arch;
    }

    private final String os;
    private final String arch;

    Platform(String os, String arch) {
        this.os = os;
        this.arch = arch;
    }
    private static String detectOS(String osName) {
        if (osName.contains("win")) return "windows";
        if (osName.contains("mac")) return "mac";
        if (osName.contains("nux") || osName.contains("nix")) return "linux";
        return "unknown";
    }

    private static String normalizeArch(String arch) {
        if (arch.equals("x86_64") || arch.equals("amd64")) return "amd64";
        if (arch.equals("aarch64") || arch.equals("arm64")) return "arm64";
        return arch;
    }
    public static Platform detect() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String archName = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        String os = detectOS(osName);
        String arch = normalizeArch(archName);

        for (Platform p : values()) {
            if (p.os.equals(os) && p.arch.equals(arch)) {
                return p;
            }
        }
        return UNKNOWN;
    }
    public String getFileExtension() {
        return switch (this.os) {
            case "windows" -> ".exe";
            case "mac" -> ".tar.gz";
            default -> "";
        };
    }
}
