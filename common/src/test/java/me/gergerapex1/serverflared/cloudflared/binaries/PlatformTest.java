package me.gergerapex1.serverflared.cloudflared.binaries;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlatformTest {
    private String origOs;
    private String origArch;

    @AfterEach
    void restore() {
        if (origOs != null) System.setProperty("os.name", origOs);
        if (origArch != null) System.setProperty("os.arch", origArch);
    }

    @Test
    void detect_windows_amd64() {
        origOs = System.getProperty("os.name");
        origArch = System.getProperty("os.arch");

        System.setProperty("os.name", "Windows 10");
        System.setProperty("os.arch", "amd64");

        Platform p = Platform.detect();
        assertEquals(Platform.WINDOWS_AMD64, p);
    }

    @Test
    void detect_linux_amd64() {
        origOs = System.getProperty("os.name");
        origArch = System.getProperty("os.arch");

        System.setProperty("os.name", "Linux");
        System.setProperty("os.arch", "x86_64");

        Platform p = Platform.detect();
        assertEquals(Platform.LINUX_AMD64, p);
    }

    @Test
    void detect_unknown() {
        origOs = System.getProperty("os.name");
        origArch = System.getProperty("os.arch");

        System.setProperty("os.name", "Solaris");
        System.setProperty("os.arch", "sparc");

        Platform p = Platform.detect();
        assertEquals(Platform.UNKNOWN, p);
    }
}

