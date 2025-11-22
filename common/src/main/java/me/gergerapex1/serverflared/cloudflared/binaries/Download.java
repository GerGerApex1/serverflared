package me.gergerapex1.serverflared.cloudflared.binaries;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import me.gergerapex1.serverflared.Constants;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;

public class Download {
    private static final String BASE_URL = "https://github.com/cloudflare/cloudflared/releases/latest/download/";
    private static final int BUFFER_SIZE = 8192;
    private static final int HTTP_OK = 200;
    
    public static void binary(String archiveName, String filename, String savedDir) {
        try {
            String downloadUrl = BASE_URL + archiveName;
            Path outputPath = prepareOutputPath(savedDir, filename);
            
            try (CloseableHttpClient httpClient = createHttpClient()) {
                downloadFile(httpClient, downloadUrl, outputPath);
            }
        } catch (IOException e) {
            Constants.LOG.error("Failed to download binary: {}", e.getMessage());
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
    
    private static CloseableHttpClient createHttpClient() {
        return HttpClientBuilder.create()
            .setRedirectStrategy(new LaxRedirectStrategy())
            .build();
    }
    
    private static void downloadFile(CloseableHttpClient httpClient, String downloadUrl, Path outputPath) throws IOException {
        HttpGet httpGet = new HttpGet(downloadUrl);
        httpClient.execute(httpGet, classicHttpResponse -> {
            int code = classicHttpResponse.getStatusLine().getStatusCode();
            if (code == HTTP_OK) {
                HttpEntity entity = classicHttpResponse.getEntity();
                if (entity != null) {
                    copyStreamToFile(entity.getContent(), outputPath);
                }
                EntityUtils.consume(entity);
            } else {
                Constants.LOG.error("Failed to download binary: HTTP {}", code);
            }
            return classicHttpResponse;
        });
    }
    
    private static void copyStreamToFile(InputStream inputStream, Path outputPath) throws IOException {
        try (InputStream in = inputStream;
             OutputStream out = Files.newOutputStream(outputPath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
}
