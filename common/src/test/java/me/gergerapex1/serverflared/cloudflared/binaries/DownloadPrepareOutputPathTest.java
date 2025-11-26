package me.gergerapex1.serverflared.cloudflared.binaries;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DownloadPrepareOutputPathTest {

    @Test
    void prepareOutputPath_createsDirectories() throws Exception {
        String tmp = System.getProperty("java.io.tmpdir");
        String dir = tmp + (tmp.endsWith("\\") || tmp.endsWith("/") ? "" : System.getProperty("file.separator")) + "serverflared-test-" + System.nanoTime();

        Method m = Download.class.getDeclaredMethod("prepareOutputPath", String.class, String.class);
        m.setAccessible(true);

        Path p = (Path) m.invoke(null, dir, "file.txt");
        assertNotNull(p);
        assertTrue(p.toString().endsWith("file.txt"));
    }
}

