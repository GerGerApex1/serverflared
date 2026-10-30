package me.gergerapex1.serverflared.cloudflared.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import me.gergerapex1.serverflared.Constants;

public class TunnelConfig {

	@JsonProperty("tunnel")
	private String tunnelUuid;

	@JsonProperty("credentials-file")
	private String credentialsFile;

	@JsonProperty("ingress")
	private List<IngressRule> ingress;

	public TunnelConfig(String hostname, String serviceUrl, String tunnelId) {
		this.tunnelUuid = tunnelId;
		this.credentialsFile = buildCredentialsFilePath(tunnelId);

		this.ingress = new ArrayList<>();
		// Primary route: e.g., hostname: "mc.example.com", service: "tcp://localhost:25565"
		this.ingress.add(new IngressRule(hostname, serviceUrl));
		// Mandatory catch-all rule for cloudflared ingress validation
		this.ingress.add(new IngressRule(null, "http_status:404"));
	}

	private static String buildCredentialsFilePath(String tunnelId) {
		String userHome = System.getProperty("user.home");
		return Paths.get(userHome, Constants.CLOUDFLARED_DIR_NAME, tunnelId + ".json").toString();
	}

	public String getTunnelUuid() {
		return tunnelUuid;
	}

	public void setTunnelUuid(String tunnelUuid) {
		this.tunnelUuid = tunnelUuid;
	}

	public String getCredentialsFile() {
		return credentialsFile;
	}

	public void setCredentialsFile(String credentialsFile) {
		this.credentialsFile = credentialsFile;
	}

	public List<IngressRule> getIngress() {
		return ingress;
	}

	public void setIngress(List<IngressRule> ingress) {
		this.ingress = ingress;
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class IngressRule {
		@JsonProperty("hostname")
		private String hostname;

		@JsonProperty("service")
		private String service;

		public IngressRule() {}

		public IngressRule(String hostname, String service) {
			this.hostname = hostname;
			this.service = service;
		}

		public String getHostname() {
			return hostname;
		}

		public void setHostname(String hostname) {
			this.hostname = hostname;
		}

		public String getService() {
			return service;
		}

		public void setService(String service) {
			this.service = service;
		}
	}
}
