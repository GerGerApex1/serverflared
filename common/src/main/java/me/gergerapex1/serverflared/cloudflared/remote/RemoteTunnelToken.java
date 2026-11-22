package me.gergerapex1.serverflared.cloudflared.remote;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Parses and validates a Cloudflare Tunnel run token offline.
 * Cloudflare tunnel tokens are Base64-encoded JSON objects containing:
 * {"a": "<accountTag>", "t": "<tunnelID>", "s": "<tunnelSecret>"}
 */
public final class RemoteTunnelToken {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

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

        validateTunnelUuid(tunnelId);
        validateTunnelSecret(secret);

        // Normalize to canonical padded standard Base64 so cloudflared always receives a valid token.
        String canonicalToken = Base64.getEncoder().encodeToString(decodedBytes);
        return new RemoteTunnelToken(canonicalToken, accountId, tunnelId);
    }

    private static void validateTunnelSecret(String secret) {
        byte[] decodedSecret;
        try {
            decodedSecret = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cloudflare tunnel token secret ('s') is not valid Base64.", e);
        }
        if (decodedSecret == null || decodedSecret.length == 0) {
            throw new IllegalArgumentException("Cloudflare tunnel token secret ('s') decodes to empty bytes.");
        }
        if (!Base64.getEncoder().encodeToString(decodedSecret).equals(secret)) {
            throw new IllegalArgumentException("Cloudflare tunnel token secret ('s') is not canonical padded Base64.");
        }
    }

    private static void validateTunnelUuid(String tunnelId) {
        if (!UUID_PATTERN.matcher(tunnelId).matches()) {
            throw new IllegalArgumentException("Cloudflare tunnel token contains an invalid tunnel ID UUID.");
        }
        try {
            UUID.fromString(tunnelId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cloudflare tunnel token contains an invalid tunnel ID UUID.", e);
        }
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
