package me.gergerapex1.serverflared.cloudflared.handler;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CloudFlaredHandlerTest {

    @Test
    void getBinaryPath_returnsProvidedPath() {
        CloudFlaredHandler handler = new CloudFlaredHandler("/usr/bin/cloudflared");
        assertEquals("/usr/bin/cloudflared", handler.getBinaryPath());
    }

    @Test
    void isDefaultTunnelUUID_detectsZeroAndNonZero() {
        CloudFlaredHandler handler = new CloudFlaredHandler("/usr/bin/cloudflared");
        String zeroUuid = new UUID(0, 0).toString();
        assertTrue(handler.isDefaultTunnelUUID(zeroUuid));

        String random = UUID.randomUUID().toString();
        assertFalse(handler.isDefaultTunnelUUID(random));
    }
}

