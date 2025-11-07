package me.gergerapex1.serverflared.cloudflared.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.cloudflared.binaries.BinaryHandler;
import me.gergerapex1.serverflared.cloudflared.binaries.Download;
import me.gergerapex1.serverflared.cloudflared.binaries.Platform;
import me.gergerapex1.serverflared.platform.Services;

public class CloudFlaredHandler {
    private final String binaryPath;
    private final CloudflaredProcessHandler processHandler;
    private static final Pattern URL_REGEX = Pattern.compile("^((https?|ftp)://|(www|ftp)\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+([/?].*)?$");
    ///private static final Pattern TUNNEL_INFO_REGEX = Pattern.compile("^(?:NAME:\\s+([A-Za-z0-9_-]+)|ID:\\s+([0-9a-fA-F-]+)|CREATED:\\s+([\\dT:\\-Z]+))$\n");
    private static final Pattern TUNNEL_INFO_REGEX = Pattern.compile("^(?:NAME:\\s+([A-Za-z0-9_-]+)|ID:\\s+([0-9a-fA-F-]+))$");

    public CloudFlaredHandler(String binaryPath) {
        this.processHandler = new CloudflaredProcessHandler(binaryPath);
        this.binaryPath = binaryPath;
    }
    public static CloudFlaredHandler createInstance() {
        if(BinaryHandler.binaryExistInPath()) {
            if(BinaryHandler.checkCloudflaredVersion("cloudflared")) {
                return new CloudFlaredHandler("cloudflared");
            }
        } else {
            Path binariesFolder = Paths.get(Services.PLATFORM.getGameDirectory().toString(), "binaries");
            if(Files.notExists(binariesFolder)) {
                try {
                    Files.createDirectories(binariesFolder);
                } catch (Exception e) {
                    Constants.LOG.error(e);
                }
            }
            Platform osArch = Platform.detect();
            String binaryName = "cloudflared-" + osArch.getOs() + "-" + osArch.getArch() + "-" +osArch.getFileExtension();
            Download.binary(binaryName, binaryName, binariesFolder.toString());
            if(osArch.getOs().equals("mac")) {
                ProcessBuilder processBuilder = new ProcessBuilder("tar", "-xvzf", Paths.get(binariesFolder.toString(), binaryName).toString(), "-C", binariesFolder.toString());
                try {
                    Process process = processBuilder.start();
                    process.waitFor();
                } catch (IOException | InterruptedException e) {
                    Constants.LOG.error(e);
                }
            }
            return new CloudFlaredHandler(Paths.get(binariesFolder.toString(), "cloudflared" + osArch.getFileExtension()).toString());
        }
        return null;
    }
    public Path getCloudflaredDirectory() {
        String userHome = System.getenv("USERPROFILE");
        if (userHome == null || userHome.isEmpty()) {
            userHome = System.getProperty("user.home");
        }

        String os = System.getProperty("os.name").toLowerCase();
        Path[] pathLocations;
        Path userhomePath = Paths.get(userHome, ".cloudflared");
        if (os.contains("win")) {
            pathLocations = new Path[] {
                userhomePath
            };
        } else {
            pathLocations = new Path[] {
                userhomePath,
                Paths.get("/etc/cloudflared"),
                Paths.get("/usr/local/etc/cloudflared")
            };
        }

        for (Path p : pathLocations) {
            try {
                if (Files.exists(p)) {
                    Constants.LOG.debug("Cloudflared directory located at {}", p);
                    return p;
                }
            } catch (Exception e) {
                Constants.LOG.error("Failed to check cloudflared cert at {}", p, e);
            }
        }
        return null;
    }
    public boolean isAuthenticated() {
        Path cloudflaredDir = getCloudflaredDirectory();
        if (cloudflaredDir != null) {
            Path certFile = cloudflaredDir.resolve("cert.pem");
            if (Files.exists(certFile)) {
                Constants.LOG.debug("Cloudflared is authenticated.");
                return true;
            } else {
                Constants.LOG.debug("Cloudflared cert.pem not found, not authenticated.");
            }
        } else {
            Constants.LOG.debug("Cloudflared directory not found, not authenticated.");
        }
        return false;
    }
    public void authenticate() {
        try {
            Process process = processHandler.executeCommandAsync("tunnel", "login");
            Thread processThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;

                    while ((line = reader.readLine()) != null) {
                        Matcher m = URL_REGEX.matcher(line  );

                        if (m.matches()) {
                            Constants.LOG.info("Please authenticate cloudflared by visiting the following URL in your browser:");
                            Constants.LOG.info(m.group());
                            Constants.LOG.info("The program will open a browser window in the machine running this server.");
                            Constants.LOG.info("If you are running this server on a headless machine, please copy and paste the URL into a browser on another device.");
                        }
                    }
                    process.waitFor();
                    Constants.LOG.info("Cloudflared authentication completed.");
                } catch (IOException | InterruptedException e) {
                    Constants.LOG.error("Authentication error", e);
                }
            }, "Cloudflared-Auth-Thread");
            processThread.setDaemon(true);
            processThread.start();
        } catch (Exception e) {
            Constants.LOG.error("Failed to start authentication", e);
        }
    }
    public TunnelInfo getTunnelInfo(TunnelInfo tunnel) {
        // try with name first then id
        TunnelInfo info = getTunnelInfo(tunnel.getName());
        if(info == null) {
            if(tunnel.getId().equals(new UUID(0,0).toString())) return null;
            info = getTunnelInfo(tunnel.getId());
            if (info == null) {
                Constants.LOG.debug("Tunnel with ID or Name '{}' not found.", tunnel.getId());
                return null;
            }
        }
        return info;
    }
    public TunnelInfo getTunnelInfo(String tunnelIdOrName) {
        TunnelInfo info = new TunnelInfo();
        try {
            Process process = processHandler.executeCommandAsync("tunnel", "info", tunnelIdOrName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = TUNNEL_INFO_REGEX.matcher(line.trim());
                Constants.LOG.info(line);
                if(line.toLowerCase().contains("is neither the id nor the name of any of your tunnels") |
                line.toLowerCase().contains("but found 0 tunnels")) {
                    Constants.LOG.error("Tunnel with ID or Name '{}' not found.", tunnelIdOrName);
                    return null;
                }

                if (matcher.find()) {
                    if (matcher.group(1) != null) {
                        info.setName(matcher.group(1));
                    } else if (matcher.group(2) != null) {
                        info.setId(matcher.group(2));
                        // TODO: Handle created date if needed
                        // TODO: handle connector data
                    /* } else if (matcher.group(3) != null) {
                        try {
                            info.setCreated(Date.from(Instant.parse(matcher.group(3))));
                            Constants.LOG.info(matcher.group(3));
                        } catch (DateTimeParseException e) {
                            info.setCreated(null);
                            System.err.println("Invalid date format: " + matcher.group(3));
                        }
                    */ }
                }
            }
        } catch (IOException e) {
            Constants.LOG.error("error", e);
        }
        return info;
    }
    public String getBinaryPath() {
        return binaryPath;
    }

}

