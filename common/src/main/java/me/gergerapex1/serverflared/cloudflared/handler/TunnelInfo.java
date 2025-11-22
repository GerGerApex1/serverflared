package me.gergerapex1.serverflared.cloudflared.handler;

import java.util.Date;

public class TunnelInfo {
    String id;
    String name;
    Date created;
    Connector[] connectors;
    
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
    
    public Connector[] getConnectors() {
        return connectors;
    }
    
    public void setConnectors(Connector[] connectors) {
        this.connectors = connectors;
    }
    
    public static class Connector {
        String connectorId;
        String created;
        String architecture;
        String version;
        String originIp;
        String edge;

        public String getEdge() {
            return edge;
        }

        public void setEdge(String edge) {
            this.edge = edge;
        }

        public String getOriginIp() {
            return originIp;
        }

        public void setOriginIp(String originIp) {
            this.originIp = originIp;
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

        public String getConnectorId() {
            return connectorId;
        }

        public void setConnectorId(String connectorId) {
            this.connectorId = connectorId;
        }

        public String getCreated() {
            return created;
        }

        public void setCreated(String created) {
            this.created = created;
        }
    }
}
