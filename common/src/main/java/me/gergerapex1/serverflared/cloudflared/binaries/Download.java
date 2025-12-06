package me.gergerapex1.serverflared.cloudflared.binaries;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import me.gergerapex1.serverflared.Constants;

public class Download {
    private static final String BASE_URL = "https://github.com/cloudflare/cloudflared/releases/latest/download/";
    private static final int BUFFER_SIZE = 8192;
    private static final int HTTP_OK = 200;
    private static final int MAX_REDIRECTS = 5;

    public static void binary(String archiveName, String filename, String savedDir) {
        try {
            String downloadUrl = BASE_URL + archiveName;
            Path outputPath = prepareOutputPath(savedDir, filename);

            URI uri = URI.create(downloadUrl);
            downloadFile(uri, outputPath);
        } catch (IOException e) {
            Constants.LOG.error("Failed to download binary: {}", e.getMessage(), e);
        }
    }

    private static Path prepareOutputPath(String savedDir, String filename) throws IOException {
        Path outputPath = Paths.get(savedDir + File.separator + filename);
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return outputPath;
    }

    private static void downloadFile(URI uri, Path outputPath) throws IOException {
        HttpURLConnection conn = openConnectionFollowRedirects(uri);
        int code = conn.getResponseCode();
        if (code == HTTP_OK) {
            long contentLength = conn.getContentLengthLong();
            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(outputPath)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalRead = 0;
                int lastLoggedPercent = -1;
                long lastLoggedBytes = 0;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    if (contentLength > 0) {
                        int percent = (int) (totalRead * 100 / contentLength);
                        if (percent != lastLoggedPercent && percent % 5 == 0) {
                            Constants.LOG.info("Downloading {}: {}% ({}/{})", outputPath.getFileName(), percent, totalRead, contentLength);
                            lastLoggedPercent = percent;
                        }
                    } else {
                        // Unknown total size - log every ~1MB progress
                        if (totalRead - lastLoggedBytes >= 1_000_000) {
                            Constants.LOG.info("Downloading {}: {} bytes", outputPath.getFileName(), totalRead);
                            lastLoggedBytes = totalRead;
                        }
                    }
                }
            } finally {
                conn.disconnect();
            }
        } else {
            Constants.LOG.error("Failed to download binary: HTTP {}", code);
            conn.disconnect();
        }
    }

    private static HttpURLConnection openConnectionFollowRedirects(URI uri) throws IOException {
        URI current = uri;
        int redirects = 0;
        while (redirects <= MAX_REDIRECTS) {
            HttpURLConnection conn = (HttpURLConnection) current.toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.connect();
            int code = conn.getResponseCode();
            if (code == HTTP_OK) {
                return conn;
            }
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null) throw new IOException("Redirect without Location header");
                current = current.resolve(loc);
                redirects++;
                continue;
            }
            return conn; // return connection (non-OK and non-redirect)
        }
        throw new IOException("Too many redirects when trying to download: " + uri);
    }
}
