package me.gergerapex1.serverflared.cloudflared.binaries;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class BinaryHandlerTest {

    @Test
    void isValidCloudflaredPath_windows() throws Exception {
        Method m = BinaryHandler.class.getDeclaredMethod("isValidCloudflaredPath", String.class, String.class);
        m.setAccessible(true);

        boolean result = (boolean) m.invoke(null, "C:\\Program Files\\cloudflared.exe", "where");
        assertTrue(result);

        result = (boolean) m.invoke(null, "C:\\some\\other\\binary.exe", "where");
        assertFalse(result);
    }

    @Test
    void isValidCloudflaredPath_unix() throws Exception {
        Method m = BinaryHandler.class.getDeclaredMethod("isValidCloudflaredPath", String.class, String.class);
        m.setAccessible(true);

        boolean result = (boolean) m.invoke(null, "cloudflared", "which");
        assertTrue(result);

        result = (boolean) m.invoke(null, "cloudflared-other", "which");
        assertFalse(result);
    }

    @Test
    void checkCloudflaredVersion_nonExistingPath_returnsFalse() {
        // This should safely return false and not throw (the implementation catches IOExceptions)
        assertFalse(BinaryHandler.checkCloudflaredVersion("does-not-exist-12345"));
    }
}

