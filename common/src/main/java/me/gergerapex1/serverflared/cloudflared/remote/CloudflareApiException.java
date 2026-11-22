package me.gergerapex1.serverflared.cloudflared.remote;

public class CloudflareApiException extends Exception {
    private final int httpStatus;

    public CloudflareApiException(String message) {
        this(-1, message, null);
    }

    public CloudflareApiException(int httpStatus, String message) {
        this(httpStatus, message, null);
    }

    public CloudflareApiException(int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
