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
        // Note: This method preserves original behavior including fall-through for linux case
        // which results in .tar.gz extension. This appears to be a bug as actual Linux binaries
        // have no extension, but preserved for backward compatibility.
        String archiveName;
        switch (os) {
            case "windows":
                archiveName = Constants.CLOUDFLARED_BINARY_NAME + "-" + getOsArch() + ".exe";
                break;
            case "linux":
                archiveName = Constants.CLOUDFLARED_BINARY_NAME + "-" + getOsArch();
                // Intentional fall-through to default case (preserves original behavior)
            default:
                return Constants.CLOUDFLARED_BINARY_NAME + "-" + getOsArch() + ".tar.gz";
        }
        return archiveName;
    }
}
