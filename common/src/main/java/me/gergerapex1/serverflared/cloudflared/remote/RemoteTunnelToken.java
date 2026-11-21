package me.gergerapex1.serverflared.cloudflared.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Parses and validates a Cloudflare Tunnel run token offline.
 * Cloudflare tunnel tokens are Base64-encoded JSON objects containing:
 * {"a": "<accountTag>", "t": "<tunnelID>", "s": "<tunnelSecret>"}
 */
public final class RemoteTunnelToken {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String rawToken;
    private final String accountId;
    private final String tunnelId;

    private RemoteTunnelToken(String rawToken, String accountId, String tunnelId) {
        this.rawToken = rawToken;
        this.accountId = accountId;
        this.tunnelId = tunnelId;
    }

    public static RemoteTunnelToken parseAndValidate(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Cloudflare tunnel token is empty.");
        }
        String trimmed = rawToken.trim();
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            try {
                decodedBytes = Base64.getUrlDecoder().decode(trimmed);
            } catch (IllegalArgumentException e2) {
                throw new IllegalArgumentException("Cloudflare tunnel token is not valid Base64.", e2);
            }
        }

        String json = new String(decodedBytes, StandardCharsets.UTF_8);
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cloudflare tunnel token payload is not valid JSON.", e);
        }

        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Cloudflare tunnel token payload must be a JSON object.");
        }

        String accountId = getNonEmptyText(root, "a");
        String tunnelId = getNonEmptyText(root, "t");
        String secret = getNonEmptyText(root, "s");

        if (accountId == null || tunnelId == null || secret == null) {
            throw new IllegalArgumentException(
                "Cloudflare tunnel token is missing required fields ('a', 't', or 's')."
            );
        }

        return new RemoteTunnelToken(trimmed, accountId, tunnelId);
    }

    private static String getNonEmptyText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual()) {
            return null;
        }
        String text = node.asText().trim();
        return text.isEmpty() ? null : text;
    }

    public String getRawToken() {
        return rawToken;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getTunnelId() {
        return tunnelId;
    }

    @Override
    public String toString() {
        return "RemoteTunnelToken{accountId='" + accountId + "', tunnelId='" + tunnelId + "', token='***REDACTED***'}";
    }
}
