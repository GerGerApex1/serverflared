package me.gergerapex1.serverflared.cloudflared.binaries;

import me.gergerapex1.serverflared.Constants;

public enum ArchVersions {
    LINUX_AMD64("linux", "amd64"),
    LINUX_X86("linux", "386"),
    LINUX_ARM("linux", "arm"),
    LINUX_ARM64("linux", "arm64"),
    WINDOWS_AMD64("windows", "amd64"),
    WINDOWS_X86("windows", "386");
    //macOS support soon

    private final String os;
    private final String arch;
    
    ArchVersions(String os, String arch) {
        this.os = os;
        this.arch = arch;
    }
    
    public String getOsArch() {
        return os + "-" + arch;
    }
    
    public String getArchiveName() {
        return switch (os) {
            case "windows" -> Constants.CLOUDFLARED_BINARY_NAME + "-" + getOsArch() + ".exe";
            case "linux" -> Constants.CLOUDFLARED_BINARY_NAME + "-" + getOsArch();
            default -> Constants.CLOUDFLARED_BINARY_NAME + "-" + getOsArch() + ".tar.gz";
        };
    }
}
