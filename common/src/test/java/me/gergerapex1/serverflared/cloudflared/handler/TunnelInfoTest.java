package me.gergerapex1.serverflared.cloudflared.handler;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class TunnelInfoTest {

    @Test
    void gettersAndSetters_work() {
        TunnelInfo info = new TunnelInfo();
        info.setId("abc123");
        info.setName("mytunnel");

        assertEquals("abc123", info.getId());
        assertEquals("mytunnel", info.getName());
    }
}

