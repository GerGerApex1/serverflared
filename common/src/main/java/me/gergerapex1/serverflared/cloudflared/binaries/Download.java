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
    public static void binary(String archiveName, String filename, String savedDir) {
        try {
            // ProgressBarBuilder barBuilder = new ProgressBarBuilder()
            //     .setTaskName("Downloading cloudflared")
            //     .setConsumer(new DelegatingProgressBarConsumer(logger::info));
            String downloadUrl = BASE_URL + archiveName;

            Path outputPath = Paths.get(savedDir + File.separator + filename);
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            HttpClientBuilder builder = HttpClientBuilder.create()
                .setRedirectStrategy(new LaxRedirectStrategy());

            try (CloseableHttpClient httpClient = builder.build()) {
                HttpGet httpGet = new HttpGet(downloadUrl);
                httpClient.execute(httpGet, classicHttpResponse -> {
                    int code = classicHttpResponse.getStatusLine().getStatusCode();
                    if (code == 200) {
                        HttpEntity entity = classicHttpResponse.getEntity();
                        if (entity != null) {
                            try (InputStream inputStream = entity.getContent();
                                OutputStream out = Files.newOutputStream(outputPath);) {
                                    byte[] buffer = new byte[8192];
                                    int bytesRead;
                                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                                        out.write(buffer, 0, bytesRead);
                                        //ProgressBar.wrap(inputStream, barBuilder);
                                    }
                            }
                        }
                        EntityUtils.consume(entity);
                    } else {
                        Constants.LOG.error("Failed to download binary: {}", code);
                    }
                    return classicHttpResponse;
                });
            }
        } catch (IOException e) {
            Constants.LOG.error("Failed to download binary: {}", e.getMessage());
        }
    }
}
