package me.gergerapex1.serverflared.cloudflared.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import me.gergerapex1.serverflared.Constants;

/**
 * Java 8-compatible Cloudflare REST API v4 client for remotely-managed tunnels.
 */
public class CloudflareApiClient {
    public static final String DEFAULT_BASE_URL = "https://api.cloudflare.com/client/v4";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final String ZERO_UUID = new UUID(0, 0).toString();

    private final String apiToken;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public CloudflareApiClient(String apiToken) {
        this(apiToken, DEFAULT_BASE_URL);
    }

    public CloudflareApiClient(String apiToken, String baseUrl) {
        if (apiToken == null || apiToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Cloudflare API token must not be empty.");
        }
        this.apiToken = apiToken.trim();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = new ObjectMapper();
    }

    public static final class RemoteTunnelRecord {
        private final String id;
        private final String name;
        private final boolean remoteConfig;
        private final String token;

        public RemoteTunnelRecord(String id, String name, boolean remoteConfig, String token) {
            this.id = id;
            this.name = name;
            this.remoteConfig = remoteConfig;
            this.token = token;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public boolean isRemoteConfig() {
            return remoteConfig;
        }

        public String getToken() {
            return token;
        }
    }

    public String resolveAccountId() throws CloudflareApiException {
        JsonNode result = request("GET", "/accounts", null);
        if (result == null || !result.isArray() || result.size() == 0) {
            throw new CloudflareApiException(
                "No Cloudflare accounts found for the provided API token. "
                    + "Ensure Account:Account Settings:Read is granted or set remote.accountId in config.yml."
            );
        }
        if (result.size() > 1) {
            throw new CloudflareApiException(
                "Multiple Cloudflare accounts (" + result.size() + ") are accessible with this API token. "
                    + "Please specify remote.accountId explicitly in config.yml."
            );
        }
        String accountId = result.get(0).path("id").asText("").trim();
        if (accountId.isEmpty()) {
            throw new CloudflareApiException("Cloudflare /accounts response did not include an account ID.");
        }
        return accountId;
    }

    public String resolveZoneId(String hostName) throws CloudflareApiException {
        if (hostName == null || hostName.trim().isEmpty()) {
            throw new CloudflareApiException("Cannot resolve Cloudflare Zone ID: hostName is empty.");
        }
        String normalized = hostName.trim().toLowerCase();
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String[] labels = normalized.split("\\.");
        if (labels.length < 2) {
            throw new CloudflareApiException("Invalid hostName '" + hostName + "' for Cloudflare Zone lookup.");
        }

        // Walk up domain labels: e.g. mc.play.example.com -> play.example.com -> example.com
        for (int i = 0; i <= labels.length - 2; i++) {
            StringBuilder candidate = new StringBuilder();
            for (int j = i; j < labels.length; j++) {
                if (j > i) {
                    candidate.append('.');
                }
                candidate.append(labels[j]);
            }
            JsonNode result = request("GET", "/zones?name=" + urlEncode(candidate.toString()), null);
            if (result != null && result.isArray() && result.size() > 0) {
                String zoneId = result.get(0).path("id").asText("").trim();
                if (!zoneId.isEmpty()) {
                    Constants.LOG.debug("Resolved Cloudflare Zone ID {} for domain {}", zoneId, candidate);
                    return zoneId;
                }
            }
        }

        throw new CloudflareApiException(
            "Could not find a Cloudflare Zone matching hostName '" + hostName + "'. "
                + "Ensure Zone:Zone:Read is granted or set remote.zoneId in config.yml."
        );
    }

    public RemoteTunnelRecord findTunnel(String accountId, String tunnelId, String tunnelName)
        throws CloudflareApiException {
        if (tunnelName != null && !tunnelName.trim().isEmpty()) {
            JsonNode result = request(
                "GET",
                "/accounts/" + urlEncode(accountId) + "/cfd_tunnel?name=" + urlEncode(tunnelName.trim()) + "&is_deleted=false",
                null
            );
            if (result != null && result.isArray() && result.size() > 0) {
                return parseTunnelRecord(result.get(0));
            }
        }

        if (tunnelId != null && !tunnelId.trim().isEmpty() && !ZERO_UUID.equals(tunnelId.trim())) {
            try {
                JsonNode result = request(
                    "GET",
                    "/accounts/" + urlEncode(accountId) + "/cfd_tunnel/" + urlEncode(tunnelId.trim()),
                    null
                );
                if (result != null && result.isObject() && result.path("deleted_at").isNull()) {
                    return parseTunnelRecord(result);
                }
            } catch (CloudflareApiException e) {
                if (e.getHttpStatus() != 404) {
                    throw e;
                }
            }
        }

        return null;
    }

    public RemoteTunnelRecord createRemoteTunnel(String accountId, String tunnelName) throws CloudflareApiException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", tunnelName);
        body.put("config_src", "cloudflare");

        JsonNode result = request("POST", "/accounts/" + urlEncode(accountId) + "/cfd_tunnel", body);
        if (result == null || !result.isObject()) {
            throw new CloudflareApiException("Unexpected empty response when creating remote tunnel.");
        }
        return parseTunnelRecord(result);
    }

    public String getTunnelToken(String accountId, String tunnelId) throws CloudflareApiException {
        JsonNode result = request(
            "GET",
            "/accounts/" + urlEncode(accountId) + "/cfd_tunnel/" + urlEncode(tunnelId) + "/token",
            null
        );
        if (result == null || !result.isTextual() || result.asText().trim().isEmpty()) {
            throw new CloudflareApiException("Cloudflare API did not return a tunnel token for tunnel " + tunnelId);
        }
        return result.asText().trim();
    }

    public void updateTunnelIngress(String accountId, String tunnelId, String hostName, String serviceUrl)
        throws CloudflareApiException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode config = root.putObject("config");
        ArrayNode ingress = config.putArray("ingress");

        ObjectNode mcRule = ingress.addObject();
        mcRule.put("hostname", hostName);
        mcRule.put("service", serviceUrl);

        ObjectNode catchAll = ingress.addObject();
        catchAll.put("service", "http_status:404");

        request(
            "PUT",
            "/accounts/" + urlEncode(accountId) + "/cfd_tunnel/" + urlEncode(tunnelId) + "/configurations",
            root
        );
    }

    public void syncDnsCname(String zoneId, String hostName, String tunnelId) throws CloudflareApiException {
        String targetContent = tunnelId + ".cfargotunnel.com";
        JsonNode existing = request(
            "GET",
            "/zones/" + urlEncode(zoneId) + "/dns_records?type=CNAME&name=" + urlEncode(hostName),
            null
        );

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "CNAME");
        payload.put("name", hostName);
        payload.put("content", targetContent);
        payload.put("proxied", true);
        payload.put("ttl", 1);

        if (existing != null && existing.isArray() && existing.size() > 0) {
            JsonNode record = existing.get(0);
            String recordId = record.path("id").asText("").trim();
            String currentContent = record.path("content").asText("").trim();
            boolean currentProxied = record.path("proxied").asBoolean(false);
            if (targetContent.equalsIgnoreCase(currentContent) && currentProxied) {
                Constants.LOG.debug("DNS CNAME for {} already points to {}", hostName, targetContent);
                return;
            }
            if (!recordId.isEmpty()) {
                request(
                    "PUT",
                    "/zones/" + urlEncode(zoneId) + "/dns_records/" + urlEncode(recordId),
                    payload
                );
                Constants.LOG.info("Updated DNS CNAME {} -> {}", hostName, targetContent);
                return;
            }
        }

        request("POST", "/zones/" + urlEncode(zoneId) + "/dns_records", payload);
        Constants.LOG.info("Created DNS CNAME {} -> {}", hostName, targetContent);
    }

    private RemoteTunnelRecord parseTunnelRecord(JsonNode node) {
        String id = node.path("id").asText("").trim();
        String name = node.path("name").asText("").trim();
        boolean remoteConfig = node.path("remote_config").asBoolean(false);
        String token = node.path("token").asText("").trim();
        return new RemoteTunnelRecord(id, name, remoteConfig, token.isEmpty() ? null : token);
    }

    private JsonNode request(String method, String path, JsonNode body) throws CloudflareApiException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Authorization", "Bearer " + apiToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");

            if (body != null) {
                conn.setDoOutput(true);
                byte[] payloadBytes = objectMapper.writeValueAsBytes(body);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payloadBytes);
                }
            }

            int status = conn.getResponseCode();
            InputStream stream = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            String responseText = readAll(stream);

            JsonNode root = responseText.isEmpty() ? null : objectMapper.readTree(responseText);
            boolean success = root != null && root.path("success").asBoolean(false);

            if (status < 200 || status >= 300 || !success) {
                String errorDetail = extractCloudflareErrors(root, responseText);
                throw new CloudflareApiException(
                    status,
                    "Cloudflare API " + method + " " + path + " failed (HTTP " + status + "): " + errorDetail
                );
            }

            return root.get("result");
        } catch (CloudflareApiException e) {
            throw e;
        } catch (IOException e) {
            throw new CloudflareApiException(-1, "Cloudflare API network error on " + method + " " + path + ": " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String extractCloudflareErrors(JsonNode root, String fallback) {
        if (root != null && root.has("errors") && root.get("errors").isArray() && root.get("errors").size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode err : root.get("errors")) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                int code = err.path("code").asInt(0);
                String msg = err.path("message").asText("Unknown error");
                if (code != 0) {
                    sb.append('[').append(code).append("] ");
                }
                sb.append(msg);
            }
            return sb.toString();
        }
        return (fallback != null && !fallback.isEmpty()) ? fallback : "No error details returned";
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported", e);
        }
    }
}
