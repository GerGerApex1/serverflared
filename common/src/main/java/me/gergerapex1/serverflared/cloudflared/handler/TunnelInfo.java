package me.gergerapex1.serverflared.cloudflared.handler;

import java.util.Date;

public class TunnelInfo {
    public String id;
    public String name;
    public Date created;
    public Connector[] connectors;
    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

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
    public static class Connector {
        public String connector_id;
        public String created;
        public String architecture;
        public String version;
        public String origin_ip;
        public String edge;

        public String getEdge() {
            return edge;
        }

        public void setEdge(String edge) {
            this.edge = edge;
        }

        public String getOrigin_ip() {
            return origin_ip;
        }

        public void setOrigin_ip(String origin_ip) {
            this.origin_ip = origin_ip;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getArchitecture() {
            return architecture;
        }

        public void setArchitecture(String architecture) {
            this.architecture = architecture;
        }

        public String getConnector_id() {
            return connector_id;
        }

        public void setConnector_id(String connector_id) {
            this.connector_id = connector_id;
        }

        public String getCreated() {
            return created;
        }

        public void setCreated(String created) {
            this.created = created;
        }
    }
}
