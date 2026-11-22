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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import me.gergerapex1.serverflared.Constants;

/**
 * Java 8-compatible Cloudflare REST API v4 client for remotely-managed tunnels.
 */
public class CloudflareApiClient {
    public static final String DEFAULT_BASE_URL = "https://api.cloudflare.com/client/v4";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final String ZERO_UUID = new UUID(0, 0).toString();
    private static volatile Object cachedJava11HttpClient;
    private static volatile Boolean java11HttpClientAvailable;

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
        return resolveZoneId(null, hostName);
    }

    public String resolveZoneId(String accountId, String hostName) throws CloudflareApiException {
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

        String accountFilter = (accountId != null && !accountId.trim().isEmpty())
            ? "&account.id=" + urlEncode(accountId.trim())
            : "";

        // Walk up domain labels: e.g. mc.play.example.com -> play.example.com -> example.com
        for (int i = 0; i <= labels.length - 2; i++) {
            StringBuilder candidate = new StringBuilder();
            for (int j = i; j < labels.length; j++) {
                if (j > i) {
                    candidate.append('.');
                }
                candidate.append(labels[j]);
            }
            JsonNode result = request("GET", "/zones?name=" + urlEncode(candidate.toString()) + accountFilter, null);
            if (result != null && result.isArray() && result.size() > 0) {
                String zoneId = result.get(0).path("id").asText("").trim();
                if (!zoneId.isEmpty()) {
                    Constants.LOG.debug("Resolved Cloudflare Zone ID {} for domain {}", zoneId, candidate);
                    return zoneId;
                }
            }
        }

        throw new CloudflareApiException(
            "Could not find a Cloudflare Zone matching hostName '" + hostName + "'"
                + (accountId != null && !accountId.trim().isEmpty() ? " in account '" + accountId.trim() + "'" : "")
                + ". Ensure Zone:Zone:Read is granted or set remote.zoneId in config.yml."
        );
    }

    public void verifyZoneBelongsToAccount(String zoneId, String expectedAccountId) throws CloudflareApiException {
        if (zoneId == null || zoneId.trim().isEmpty()) {
            throw new CloudflareApiException("Cloudflare Zone ID is empty.");
        }
        if (expectedAccountId == null || expectedAccountId.trim().isEmpty()) {
            throw new CloudflareApiException("Cannot verify zone ownership: accountId is empty.");
        }
        JsonNode result = request("GET", "/zones/" + urlEncode(zoneId.trim()), null);
        String zoneAccountId = (result != null) ? result.path("account").path("id").asText("").trim() : "";
        if (zoneAccountId.isEmpty() || !expectedAccountId.trim().equals(zoneAccountId)) {
            throw new CloudflareApiException(
                "Configured Cloudflare zone '" + zoneId.trim() + "' belongs to account '"
                    + (zoneAccountId.isEmpty() ? "unknown" : zoneAccountId)
                    + "', not tunnel account '" + expectedAccountId.trim() + "'."
            );
        }
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

    public JsonNode getTunnelConfiguration(String accountId, String tunnelId) throws CloudflareApiException {
        try {
            JsonNode result = request(
                "GET",
                "/accounts/" + urlEncode(accountId) + "/cfd_tunnel/" + urlEncode(tunnelId) + "/configurations",
                null
            );
            if (result != null && result.isObject()) {
                JsonNode configNode = result.get("config");
                if (configNode != null && configNode.isObject()) {
                    return configNode.deepCopy();
                }
            }
            return null;
        } catch (CloudflareApiException e) {
            if (e.getHttpStatus() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static final class IngressUpdateReceipt {
        private final String hostName;
        private final boolean addedNewRule;
        private final ObjectNode appliedRule;
        private final ObjectNode previousRule;

        public IngressUpdateReceipt(
            String hostName,
            boolean addedNewRule,
            ObjectNode appliedRule,
            ObjectNode previousRule
        ) {
            this.hostName = hostName;
            this.addedNewRule = addedNewRule;
            this.appliedRule = appliedRule;
            this.previousRule = previousRule;
        }

        public String getHostName() {
            return hostName;
        }

        public boolean isAddedNewRule() {
            return addedNewRule;
        }

        public ObjectNode getAppliedRule() {
            return appliedRule;
        }

        public ObjectNode getPreviousRule() {
            return previousRule;
        }
    }

    public IngressUpdateReceipt updateTunnelIngress(String accountId, String tunnelId, String hostName, String serviceUrl)
        throws CloudflareApiException {
        JsonNode existingConfig = getTunnelConfiguration(accountId, tunnelId);
        return updateTunnelIngress(accountId, tunnelId, hostName, serviceUrl, existingConfig);
    }

    public IngressUpdateReceipt updateTunnelIngress(
        String accountId,
        String tunnelId,
        String hostName,
        String serviceUrl,
        JsonNode existingConfig
    ) throws CloudflareApiException {
        return updateTunnelIngress(accountId, tunnelId, hostName, serviceUrl, existingConfig, null);
    }

    public IngressUpdateReceipt updateTunnelIngress(
        String accountId,
        String tunnelId,
        String hostName,
        String serviceUrl,
        JsonNode existingConfig,
        AtomicReference<IngressUpdateReceipt> plannedReceiptOut
    ) throws CloudflareApiException {
        String normalizedTarget = normalizeHost(hostName);
        if (normalizedTarget.isEmpty()) {
            throw new CloudflareApiException("Cannot update tunnel ingress: hostName is empty.");
        }

        ObjectNode mergedConfig = (existingConfig != null && existingConfig.isObject())
            ? ((ObjectNode) existingConfig.deepCopy())
            : objectMapper.createObjectNode();

        ArrayNode mergedIngress = objectMapper.createArrayNode();
        JsonNode existingIngress = mergedConfig.get("ingress");

        boolean updatedExistingRule = false;
        int firstMatchingWildcardIndex = -1;
        ObjectNode existingCatchAll = null;
        ObjectNode appliedRule = null;
        ObjectNode previousRule = null;

        if (existingIngress != null && existingIngress.isArray()) {
            int size = existingIngress.size();
            for (int i = 0; i < size; i++) {
                JsonNode ruleNode = existingIngress.get(i);
                if (ruleNode == null || !ruleNode.isObject()) {
                    continue;
                }
                ObjectNode ruleCopy = (ObjectNode) ruleNode.deepCopy();
                String ruleHost = ruleCopy.path("hostname").asText("").trim();
                String rulePath = ruleCopy.path("path").asText("").trim();
                boolean isCatchAll = (ruleHost.isEmpty() || "*".equals(ruleHost)) && rulePath.isEmpty() && i == size - 1;

                if (isCatchAll) {
                    existingCatchAll = ruleCopy;
                    continue;
                }

                boolean exactMatch = normalizeHost(ruleHost).equals(normalizedTarget);
                boolean wildcardMatch = !exactMatch && wildcardMatchesHost(ruleHost, normalizedTarget);
                boolean hostlessPathMatch = ruleHost.isEmpty() && !rulePath.isEmpty();

                if ((exactMatch || wildcardMatch || hostlessPathMatch) && !rulePath.isEmpty()) {
                    throw new CloudflareApiException(
                        "Cannot update tunnel ingress for '" + hostName.trim()
                            + "': existing path-scoped rule ('" + (ruleHost.isEmpty() ? "*" : ruleHost) + rulePath
                            + "') overlaps this hostname and cannot be resolved without modifying existing routes."
                    );
                }

                if (wildcardMatch) {
                    if (firstMatchingWildcardIndex < 0) {
                        firstMatchingWildcardIndex = mergedIngress.size();
                    }
                    mergedIngress.add(ruleCopy);
                } else if (exactMatch) {
                    if (firstMatchingWildcardIndex >= 0) {
                        throw new CloudflareApiException(
                            "Cannot update tunnel ingress for '" + hostName.trim()
                                + "': an earlier matching wildcard rule shadows this exact-host route."
                        );
                    }
                    if (!updatedExistingRule) {
                        previousRule = (ObjectNode) ruleCopy.deepCopy();
                        ruleCopy.put("hostname", hostName.trim());
                        ruleCopy.put("service", serviceUrl);
                        appliedRule = (ObjectNode) ruleCopy.deepCopy();
                        mergedIngress.add(ruleCopy);
                        updatedExistingRule = true;
                    } else {
                        throw new CloudflareApiException(
                            "Cannot update tunnel ingress for '" + hostName.trim()
                                + "': multiple existing pathless ingress rules match this hostname; "
                                + "remove duplicate rules before retrying."
                        );
                    }
                } else {
                    mergedIngress.add(ruleCopy);
                }
            }
        }

        if (!updatedExistingRule) {
            ObjectNode mcRule = objectMapper.createObjectNode();
            mcRule.put("hostname", hostName.trim());
            mcRule.put("service", serviceUrl);
            appliedRule = (ObjectNode) mcRule.deepCopy();
            if (firstMatchingWildcardIndex >= 0) {
                mergedIngress.insert(firstMatchingWildcardIndex, mcRule);
            } else {
                mergedIngress.add(mcRule);
            }
        }

        if (existingCatchAll != null) {
            mergedIngress.add(existingCatchAll);
        } else {
            ObjectNode catchAll = objectMapper.createObjectNode();
            catchAll.put("service", "http_status:404");
            mergedIngress.add(catchAll);
        }

        mergedConfig.set("ingress", mergedIngress);

        ObjectNode root = objectMapper.createObjectNode();
        root.set("config", mergedConfig);

        IngressUpdateReceipt receipt = new IngressUpdateReceipt(
            hostName.trim(), !updatedExistingRule, appliedRule, previousRule
        );
        if (plannedReceiptOut != null) {
            plannedReceiptOut.set(receipt);
        }

        request(
            "PUT",
            "/accounts/" + urlEncode(accountId) + "/cfd_tunnel/" + urlEncode(tunnelId) + "/configurations",
            root
        );

        return receipt;
    }

    public boolean isIngressReceiptLive(String accountId, String tunnelId, IngressUpdateReceipt receipt)
        throws CloudflareApiException {
        if (receipt == null || receipt.getAppliedRule() == null) {
            return false;
        }
        JsonNode currentConfig = getTunnelConfiguration(accountId, tunnelId);
        if (currentConfig == null || !currentConfig.isObject() || !currentConfig.path("ingress").isArray()) {
            return false;
        }
        String normalizedTarget = normalizeHost(receipt.getHostName());
        JsonNode currentIngress = currentConfig.get("ingress");
        JsonNode singleMatch = null;
        int matchCount = 0;
        for (int i = 0; i < currentIngress.size(); i++) {
            JsonNode ruleNode = currentIngress.get(i);
            if (ruleNode == null || !ruleNode.isObject()) {
                continue;
            }
            String ruleHost = ruleNode.path("hostname").asText("").trim();
            String rulePath = ruleNode.path("path").asText("").trim();
            if (rulePath.isEmpty() && normalizeHost(ruleHost).equals(normalizedTarget)) {
                singleMatch = ruleNode;
                matchCount++;
            }
        }
        return matchCount == 1 && receipt.getAppliedRule().equals(singleMatch);
    }

    /**
     * Selectively rolls back only the single ingress rule touched by {@code receipt} against
     * the live tunnel configuration, preserving any intervening edits to unrelated routes.
     * Throws {@link CloudflareApiException} without overwriting if the managed rule was modified concurrently.
     */
    public void rollbackTunnelIngress(String accountId, String tunnelId, IngressUpdateReceipt receipt)
        throws CloudflareApiException {
        if (receipt == null || receipt.getAppliedRule() == null) {
            throw new CloudflareApiException("Cannot roll back ingress: missing update receipt.");
        }
        JsonNode currentConfig = getTunnelConfiguration(accountId, tunnelId);
        if (currentConfig == null || !currentConfig.isObject() || !currentConfig.path("ingress").isArray()) {
            throw new CloudflareApiException(
                "Rollback conflict for '" + receipt.getHostName()
                    + "': current tunnel configuration is unavailable; refusing to overwrite."
            );
        }

        String normalizedTarget = normalizeHost(receipt.getHostName());
        ObjectNode updatedConfig = (ObjectNode) currentConfig.deepCopy();
        ArrayNode currentIngress = (ArrayNode) updatedConfig.get("ingress");

        int matchIndex = -1;
        int matchCount = 0;
        for (int i = 0; i < currentIngress.size(); i++) {
            JsonNode ruleNode = currentIngress.get(i);
            if (ruleNode == null || !ruleNode.isObject()) {
                continue;
            }
            String ruleHost = ruleNode.path("hostname").asText("").trim();
            String rulePath = ruleNode.path("path").asText("").trim();
            if (rulePath.isEmpty() && normalizeHost(ruleHost).equals(normalizedTarget)) {
                matchIndex = i;
                matchCount++;
            }
        }

        if (matchCount != 1 || matchIndex < 0) {
            throw new CloudflareApiException(
                "Rollback conflict for '" + receipt.getHostName()
                    + "': expected 1 managed rule in live configuration but found " + matchCount
                    + "; leaving newer configuration intact."
            );
        }

        JsonNode liveRule = currentIngress.get(matchIndex);
        if (!receipt.getAppliedRule().equals(liveRule)) {
            throw new CloudflareApiException(
                "Rollback conflict for '" + receipt.getHostName()
                    + "': managed ingress rule was modified concurrently; leaving newer configuration intact."
            );
        }

        if (receipt.isAddedNewRule()) {
            currentIngress.remove(matchIndex);
        } else {
            if (receipt.getPreviousRule() == null) {
                throw new CloudflareApiException(
                    "Rollback conflict for '" + receipt.getHostName()
                        + "': missing prior rule snapshot; leaving newer configuration intact."
                );
            }
            currentIngress.set(matchIndex, receipt.getPreviousRule().deepCopy());
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.set("config", updatedConfig);

        request(
            "PUT",
            "/accounts/" + urlEncode(accountId) + "/cfd_tunnel/" + urlEncode(tunnelId) + "/configurations",
            root
        );
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String trimmed = host.trim().toLowerCase();
        if (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    static boolean wildcardMatchesHost(String ruleHost, String normalizedTargetHost) {
        String normRule = normalizeHost(ruleHost);
        if (normRule.isEmpty() || normalizedTargetHost.isEmpty()) {
            return false;
        }
        if ("*".equals(normRule)) {
            return true;
        }
        if (normRule.startsWith("*.")) {
            String suffix = normRule.substring(1); // includes leading '.'
            return normalizedTargetHost.endsWith(suffix) && normalizedTargetHost.length() > suffix.length();
        }
        return false;
    }

    public void restoreTunnelConfiguration(String accountId, String tunnelId, JsonNode previousConfig)
        throws CloudflareApiException {
        ObjectNode root = objectMapper.createObjectNode();
        if (previousConfig != null && previousConfig.isObject()) {
            root.set("config", previousConfig.deepCopy());
        } else {
            ObjectNode emptyConfig = root.putObject("config");
            ArrayNode ingress = emptyConfig.putArray("ingress");
            ObjectNode catchAll = ingress.addObject();
            catchAll.put("service", "http_status:404");
        }
        request(
            "PUT",
            "/accounts/" + urlEncode(accountId) + "/cfd_tunnel/" + urlEncode(tunnelId) + "/configurations",
            root
        );
    }

    public static final class DnsCnameRecord {
        private final String id;
        private final String name;
        private final String content;
        private final boolean proxied;
        private final int ttl;

        public DnsCnameRecord(String id, String name, String content, boolean proxied, int ttl) {
            this.id = id != null ? id.trim() : "";
            this.name = name != null ? name.trim() : "";
            this.content = content != null ? content.trim() : "";
            this.proxied = proxied;
            this.ttl = ttl;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getContent() {
            return content;
        }

        public boolean isProxied() {
            return proxied;
        }

        public int getTtl() {
            return ttl;
        }

        public boolean matchesTunnelTarget(String tunnelId) {
            if (tunnelId == null || tunnelId.trim().isEmpty()) {
                return false;
            }
            return (tunnelId.trim() + ".cfargotunnel.com").equalsIgnoreCase(content);
        }

        public boolean matchesSyncedTarget(String tunnelId) {
            return matchesTunnelTarget(tunnelId) && proxied;
        }

        public boolean matchesAppliedRecord(DnsCnameRecord applied) {
            if (applied == null) {
                return false;
            }
            if (!applied.id.isEmpty() && !this.id.equals(applied.id)) {
                return false;
            }
            if (!applied.name.isEmpty() && !normalizeHost(this.name).equals(normalizeHost(applied.name))) {
                return false;
            }
            return this.content.equalsIgnoreCase(applied.content)
                && this.proxied == applied.proxied
                && this.ttl == applied.ttl;
        }
    }

    public static final class DnsSyncReceipt {
        private final boolean changed;
        private final boolean createdNewRecord;
        private final DnsCnameRecord priorRecord;
        private final DnsCnameRecord appliedRecord;

        public DnsSyncReceipt(
            boolean changed,
            boolean createdNewRecord,
            DnsCnameRecord priorRecord,
            DnsCnameRecord appliedRecord
        ) {
            this.changed = changed;
            this.createdNewRecord = createdNewRecord;
            this.priorRecord = priorRecord;
            this.appliedRecord = appliedRecord;
        }

        public boolean isChanged() {
            return changed;
        }

        public boolean isCreatedNewRecord() {
            return createdNewRecord;
        }

        public DnsCnameRecord getPriorRecord() {
            return priorRecord;
        }

        public DnsCnameRecord getAppliedRecord() {
            return appliedRecord;
        }
    }

    public DnsCnameRecord getDnsCnameRecord(String zoneId, String hostName) throws CloudflareApiException {
        JsonNode existing = request(
            "GET",
            "/zones/" + urlEncode(zoneId) + "/dns_records?type=CNAME&name=" + urlEncode(hostName),
            null
        );
        if (existing != null && existing.isArray() && existing.size() > 0) {
            JsonNode record = existing.get(0);
            return new DnsCnameRecord(
                record.path("id").asText(""),
                record.path("name").asText(hostName),
                record.path("content").asText(""),
                record.path("proxied").asBoolean(false),
                record.path("ttl").asInt(1)
            );
        }
        return null;
    }

    public DnsSyncReceipt syncDnsCname(String zoneId, String hostName, String tunnelId) throws CloudflareApiException {
        return syncDnsCname(zoneId, hostName, tunnelId, null);
    }

    public DnsSyncReceipt syncDnsCname(
        String zoneId,
        String hostName,
        String tunnelId,
        AtomicReference<DnsCnameRecord> observedPriorOut
    ) throws CloudflareApiException {
        String targetContent = tunnelId + ".cfargotunnel.com";
        DnsCnameRecord existingRecord = getDnsCnameRecord(zoneId, hostName);
        if (observedPriorOut != null) {
            observedPriorOut.set(existingRecord);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "CNAME");
        payload.put("name", hostName);
        payload.put("content", targetContent);
        payload.put("proxied", true);
        payload.put("ttl", 1);

        if (existingRecord != null) {
            if (existingRecord.matchesSyncedTarget(tunnelId)) {
                Constants.LOG.debug("DNS CNAME for {} already points to {}", hostName, targetContent);
                return new DnsSyncReceipt(false, false, existingRecord, existingRecord);
            }
            if (!existingRecord.getId().isEmpty()) {
                // PATCH updates only the managed CNAME fields, preserving existing comments and tags.
                JsonNode updated = request(
                    "PATCH",
                    "/zones/" + urlEncode(zoneId) + "/dns_records/" + urlEncode(existingRecord.getId()),
                    payload
                );
                DnsCnameRecord applied = parseDnsCnameRecord(
                    updated, existingRecord.getId(), hostName, targetContent, true, 1
                );
                Constants.LOG.info("Updated DNS CNAME {} -> {}", hostName, targetContent);
                return new DnsSyncReceipt(true, false, existingRecord, applied);
            }
        }

        JsonNode created = request("POST", "/zones/" + urlEncode(zoneId) + "/dns_records", payload);
        DnsCnameRecord applied = parseDnsCnameRecord(created, "", hostName, targetContent, true, 1);
        Constants.LOG.info("Created DNS CNAME {} -> {}", hostName, targetContent);
        return new DnsSyncReceipt(true, true, null, applied);
    }

    private static DnsCnameRecord parseDnsCnameRecord(
        JsonNode node,
        String fallbackId,
        String fallbackName,
        String fallbackContent,
        boolean fallbackProxied,
        int fallbackTtl
    ) {
        if (node == null || !node.isObject()) {
            return new DnsCnameRecord(fallbackId, fallbackName, fallbackContent, fallbackProxied, fallbackTtl);
        }
        return new DnsCnameRecord(
            node.path("id").asText(fallbackId),
            node.path("name").asText(fallbackName),
            node.path("content").asText(fallbackContent),
            node.path("proxied").asBoolean(fallbackProxied),
            node.path("ttl").asInt(fallbackTtl)
        );
    }

    public void rollbackDnsCname(String zoneId, String hostName, String appliedTunnelId, DnsSyncReceipt receipt)
        throws CloudflareApiException {
        if (receipt == null || !receipt.isChanged()) {
            return;
        }
        DnsCnameRecord applied = receipt.getAppliedRecord();
        if (applied == null) {
            throw new CloudflareApiException(
                "DNS rollback conflict for '" + hostName + "': missing applied DNS receipt; leaving live record intact."
            );
        }
        DnsCnameRecord liveDns = getDnsCnameRecord(zoneId, hostName);
        if (liveDns == null) {
            if (receipt.isCreatedNewRecord()) {
                return;
            }
            throw new CloudflareApiException(
                "DNS rollback conflict for '" + hostName + "': live CNAME record was removed concurrently; leaving newer state intact."
            );
        }
        if (!liveDns.matchesAppliedRecord(applied)) {
            throw new CloudflareApiException(
                "DNS rollback conflict for '" + hostName
                    + "': live CNAME record ID or managed fields changed after sync; leaving newer record intact."
            );
        }
        restoreDnsCnameRecord(zoneId, hostName, appliedTunnelId, receipt.getPriorRecord(), liveDns);
    }

    public void restoreDnsCnameRecord(
        String zoneId,
        String hostName,
        String appliedTunnelId,
        DnsCnameRecord priorRecord,
        DnsCnameRecord currentRecord
    ) throws CloudflareApiException {
        if (priorRecord != null) {
            String recordId = !priorRecord.getId().isEmpty()
                ? priorRecord.getId()
                : (currentRecord != null ? currentRecord.getId() : "");
            if (recordId.isEmpty()) {
                throw new CloudflareApiException("Cannot restore DNS CNAME for '" + hostName + "': missing record ID.");
            }
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("type", "CNAME");
            payload.put("name", !priorRecord.getName().isEmpty() ? priorRecord.getName() : hostName);
            payload.put("content", priorRecord.getContent());
            payload.put("proxied", priorRecord.isProxied());
            payload.put("ttl", priorRecord.getTtl() > 0 ? priorRecord.getTtl() : 1);
            request(
                "PATCH",
                "/zones/" + urlEncode(zoneId) + "/dns_records/" + urlEncode(recordId),
                payload
            );
            Constants.LOG.info("Restored DNS CNAME {} -> {}", hostName, priorRecord.getContent());
            return;
        }

        if (currentRecord == null || currentRecord.getId().isEmpty()) {
            return;
        }
        if (appliedTunnelId != null && !appliedTunnelId.trim().isEmpty()
            && !currentRecord.matchesTunnelTarget(appliedTunnelId)) {
            throw new CloudflareApiException(
                "Refusing to delete DNS CNAME for '" + hostName
                    + "': live record no longer points to tunnel " + appliedTunnelId
            );
        }
        request(
            "DELETE",
            "/zones/" + urlEncode(zoneId) + "/dns_records/" + urlEncode(currentRecord.getId()),
            null
        );
        Constants.LOG.info("Removed newly created DNS CNAME {} during rollback.", hostName);
    }

    private RemoteTunnelRecord parseTunnelRecord(JsonNode node) {
        String id = node.path("id").asText("").trim();
        String name = node.path("name").asText("").trim();
        boolean remoteConfig = node.path("remote_config").asBoolean(false);
        String token = node.path("token").asText("").trim();
        return new RemoteTunnelRecord(id, name, remoteConfig, token.isEmpty() ? null : token);
    }

    private static final class HttpResponseData {
        final int status;
        final String body;

        HttpResponseData(int status, String body) {
            this.status = status;
            this.body = body != null ? body : "";
        }
    }

    private JsonNode request(String method, String path, JsonNode body) throws CloudflareApiException {
        try {
            byte[] payloadBytes = (body != null) ? objectMapper.writeValueAsBytes(body) : null;
            HttpResponseData response = ("PATCH".equalsIgnoreCase(method) && isJava11HttpClientAvailable())
                ? executeViaJava11HttpClient(method, path, payloadBytes)
                : executeViaHttpUrlConnection(method, path, payloadBytes);

            int status = response.status;
            boolean is2xx = status >= 200 && status < 300;
            String responseText = response.body;

            JsonNode root;
            try {
                root = responseText.isEmpty() ? null : objectMapper.readTree(responseText);
            } catch (IOException parseError) {
                if (!is2xx) {
                    throw new CloudflareApiException(
                        status,
                        "Cloudflare API " + method + " " + path + " failed (HTTP " + status + ") with unreadable response body",
                        parseError
                    );
                }
                throw new CloudflareApiException(
                    status,
                    "Cloudflare API " + method + " " + path + " returned unreadable JSON (HTTP " + status + ")",
                    parseError
                );
            }

            boolean success = root != null && root.path("success").asBoolean(false);

            if (!is2xx || !success) {
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
        }
    }

    private HttpResponseData executeViaHttpUrlConnection(String method, String path, byte[] payloadBytes)
        throws CloudflareApiException, IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + path);
            conn = (HttpURLConnection) url.openConnection();
            if ("PATCH".equalsIgnoreCase(method)) {
                setPatchMethodOnHttpUrlConnection(conn);
            } else {
                conn.setRequestMethod(method);
            }
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Authorization", "Bearer " + apiToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");

            if (payloadBytes != null) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payloadBytes);
                }
            }

            int status = conn.getResponseCode();
            boolean is2xx = status >= 200 && status < 300;
            String responseText;
            try {
                InputStream stream = is2xx ? conn.getInputStream() : conn.getErrorStream();
                responseText = readAll(stream);
            } catch (IOException readError) {
                if (!is2xx) {
                    throw new CloudflareApiException(
                        status,
                        "Cloudflare API " + method + " " + path + " failed (HTTP " + status + ") with unreadable response body",
                        readError
                    );
                }
                throw readError;
            }
            return new HttpResponseData(status, responseText);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static boolean isJava11HttpClientAvailable() {
        Boolean available = java11HttpClientAvailable;
        if (available != null) {
            return available;
        }
        try {
            Class.forName("java.net.http.HttpClient");
            java11HttpClientAvailable = Boolean.TRUE;
            return true;
        } catch (ClassNotFoundException e) {
            java11HttpClientAvailable = Boolean.FALSE;
            return false;
        }
    }

    private static Object getOrCreateJava11HttpClient() throws Exception {
        Object client = cachedJava11HttpClient;
        if (client != null) {
            return client;
        }
        synchronized (CloudflareApiClient.class) {
            if (cachedJava11HttpClient != null) {
                return cachedJava11HttpClient;
            }
            Class<?> httpClientClass = Class.forName("java.net.http.HttpClient");
            Class<?> builderClass = Class.forName("java.net.http.HttpClient$Builder");
            Object builder = httpClientClass.getMethod("newBuilder").invoke(null);
            builderClass.getMethod("connectTimeout", Duration.class)
                .invoke(builder, Duration.ofMillis(CONNECT_TIMEOUT_MS));
            Object built = builderClass.getMethod("build").invoke(builder);
            cachedJava11HttpClient = built;
            return built;
        }
    }

    private HttpResponseData executeViaJava11HttpClient(String method, String path, byte[] payloadBytes)
        throws CloudflareApiException, IOException {
        try {
            Object client = getOrCreateJava11HttpClient();
            Class<?> httpClientClass = Class.forName("java.net.http.HttpClient");
            Class<?> requestClass = Class.forName("java.net.http.HttpRequest");
            Class<?> reqBuilderClass = Class.forName("java.net.http.HttpRequest$Builder");
            Class<?> bodyPublishersClass = Class.forName("java.net.http.HttpRequest$BodyPublishers");
            Class<?> bodyPublisherClass = Class.forName("java.net.http.HttpRequest$BodyPublisher");
            Class<?> bodyHandlersClass = Class.forName("java.net.http.HttpResponse$BodyHandlers");
            Class<?> bodyHandlerClass = Class.forName("java.net.http.HttpResponse$BodyHandler");
            Class<?> responseClass = Class.forName("java.net.http.HttpResponse");

            Object bodyPublisher = (payloadBytes != null)
                ? bodyPublishersClass.getMethod("ofByteArray", byte[].class).invoke(null, new Object[]{payloadBytes})
                : bodyPublishersClass.getMethod("noBody").invoke(null);

            Object reqBuilder = requestClass.getMethod("newBuilder", URI.class)
                .invoke(null, URI.create(baseUrl + path));
            reqBuilderClass.getMethod("timeout", Duration.class)
                .invoke(reqBuilder, Duration.ofMillis(READ_TIMEOUT_MS));
            reqBuilderClass.getMethod("header", String.class, String.class)
                .invoke(reqBuilder, "Authorization", "Bearer " + apiToken);
            reqBuilderClass.getMethod("header", String.class, String.class)
                .invoke(reqBuilder, "Accept", "application/json");
            reqBuilderClass.getMethod("header", String.class, String.class)
                .invoke(reqBuilder, "Content-Type", "application/json");
            reqBuilderClass.getMethod("method", String.class, bodyPublisherClass)
                .invoke(reqBuilder, method, bodyPublisher);
            Object httpRequest = reqBuilderClass.getMethod("build").invoke(reqBuilder);

            Object bodyHandler = bodyHandlersClass.getMethod("ofString", Charset.class)
                .invoke(null, StandardCharsets.UTF_8);
            Method sendAsyncMethod = httpClientClass.getMethod("sendAsync", requestClass, bodyHandlerClass);
            CompletableFuture<?> responseFuture = (CompletableFuture<?>) sendAsyncMethod.invoke(
                client, httpRequest, bodyHandler
            );
            long overallTimeoutMs = (long) CONNECT_TIMEOUT_MS + READ_TIMEOUT_MS;
            Object httpResponse;
            try {
                httpResponse = responseFuture.get(overallTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                responseFuture.cancel(true);
                throw new SocketTimeoutException(
                    "Timed out reading response body after " + overallTimeoutMs + " ms"
                );
            } catch (InterruptedException interrupted) {
                responseFuture.cancel(true);
                Thread.currentThread().interrupt();
                throw new CloudflareApiException(
                    -1,
                    "Cloudflare API request interrupted on " + method + " " + path + ": " + interrupted.getMessage(),
                    interrupted
                );
            } catch (ExecutionException execError) {
                Throwable cause = execError.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                throw new IOException("Failed to execute HTTP " + method + " via Java 11 HttpClient", cause);
            }

            int status = (Integer) responseClass.getMethod("statusCode").invoke(httpResponse);
            String responseBody = (String) responseClass.getMethod("body").invoke(httpResponse);
            return new HttpResponseData(status, responseBody);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new CloudflareApiException(
                    -1,
                    "Cloudflare API request interrupted on " + method + " " + path + ": " + cause.getMessage(),
                    cause
                );
            }
            throw new IOException("Failed to execute HTTP " + method + " via Java 11 HttpClient", cause);
        } catch (Exception e) {
            throw new IOException("Failed to execute HTTP " + method + " via Java 11 HttpClient", e);
        }
    }

    private static void setPatchMethodOnHttpUrlConnection(HttpURLConnection conn) throws IOException {
        try {
            Object target = conn;
            while (target != null) {
                Field methodField = findField(target.getClass(), "method");
                if (methodField != null) {
                    methodField.setAccessible(true);
                    methodField.set(target, "PATCH");
                }
                Field delegateField = findField(target.getClass(), "delegate");
                if (delegateField == null) {
                    break;
                }
                delegateField.setAccessible(true);
                target = delegateField.get(target);
            }
        } catch (Exception e) {
            throw new IOException("Failed to configure HTTP PATCH method on HttpURLConnection", e);
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
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
