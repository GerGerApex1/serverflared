package me.gergerapex1.serverflared.cloudflared.handler;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Date;

public class TunnelInfo {
    private String id;
    private String name;
    private Date created;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
