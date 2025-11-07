package me.gergerapex1.serverflared.cloudflared.binaries;

public enum ArchVersions {
    LINUX_AMD64("linux", "amd64"),
    LINUX_X86("linux", "386"),
    LINUX_ARM64("linux", "arm"),
    LINUX_ARM("linux", "arm64"),
    WINDOWS_AMD64("windows", "amd64"),
    WINDOWS_X86("windows", "386"),;
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
        String archiveName;
        switch (os) {
            case "windows":
                archiveName =  "cloudflared-" + getOsArch() + ".exe";
                break;
            case "linux":
                archiveName = "cloudflared-" + getOsArch();
            default:
                return "cloudflared-" + getOsArch() + ".tar.gz";
        }
        return archiveName;
    }

}
