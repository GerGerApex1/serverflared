package me.gergerapex1.serverflared.cloudflared.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.gergerapex1.serverflared.Constants;
import me.gergerapex1.serverflared.ModPlatformInstance;
import me.gergerapex1.serverflared.cloudflared.binaries.BinaryHandler;
import me.gergerapex1.serverflared.cloudflared.binaries.Download;
import me.gergerapex1.serverflared.cloudflared.binaries.Platform;
import me.gergerapex1.serverflared.process.ProcessHandler;
import me.gergerapex1.serverflared.process.SubCommand;

public class CloudFlaredHandler {
    private final String binaryPath;
    private final ProcessHandler processHandler;
    private static final Pattern URL_REGEX = Pattern.compile("^((https?|ftp)://|(www|ftp)\\.)?[a-z0-9-]+(\\.[a-z0-9-]+)+([/?].*)?$");
    // private static final Pattern TUNNEL_INFO_REGEX = Pattern.compile("^(?:NAME:\\s+([A-Za-z0-9_-]+)|ID:\\s+([0-9a-fA-F-]+)|CREATED:\\s+([\\dT:\\-Z]+))$\n");
    private static final Pattern TUNNEL_INFO_REGEX = Pattern.compile("^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\s+(.+?)\\s+(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z)(?:\\s+(.*))?$");

    public CloudFlaredHandler(String binaryPath) {
        this.processHandler = new ProcessHandler(binaryPath);
        this.binaryPath = binaryPath;
    }
    public static CloudFlaredHandler createInstance() {
        if (BinaryHandler.binaryExistInPath()) {
            if (BinaryHandler.checkCloudflaredVersion(Constants.CLOUDFLARED_BINARY_NAME)) {
                return new CloudFlaredHandler(Constants.CLOUDFLARED_BINARY_NAME);
            }
        }

        return createInstanceFromDownloadedBinary();
    }

    private static CloudFlaredHandler createInstanceFromDownloadedBinary() {
        Path binariesFolder = createBinariesFolder();
        if (binariesFolder == null) {
            return null;
        }

        Platform osArch = Platform.detect();
        String binaryName = buildBinaryName(osArch);

        Path existing = findExistingBinary(binariesFolder);
        if (existing != null) {
            setExecutableIfNeeded(existing, osArch);
            return new CloudFlaredHandler(existing.toString());
        }

        Download.binary(binaryName, binaryName, binariesFolder.toString());

        if (osArch.getOs().equals("mac")) {
            extractMacBinary(binariesFolder, binaryName);
        }

        Path after = findExistingBinary(binariesFolder);
        if (after != null) {
            setExecutableIfNeeded(after, osArch);
            return new CloudFlaredHandler(after.toString());
        }

        // 5) Fallback to previously constructed path (original behavior).
        String binaryPath = Paths.get(binariesFolder.toString(),
            Constants.CLOUDFLARED_BINARY_NAME + osArch.getFileExtension()).toString();
        return new CloudFlaredHandler(binaryPath);
    }

    private static Path createBinariesFolder() {
        Path binariesFolder = Paths.get(ModPlatformInstance.xplat().getGameDirectory().toString(), Constants.BINARIES_DIR);
        if (Files.notExists(binariesFolder)) {
            try {
                Files.createDirectories(binariesFolder);
            } catch (Exception e) {
                Constants.LOG.error("Failed to create binaries folder", e);
                return null;
            }
        }
        return binariesFolder;
    }

    private static String buildBinaryName(Platform osArch) {
        return Constants.CLOUDFLARED_BINARY_NAME + "-" + osArch.getOs() + "-" +
               osArch.getArch() + "-" + osArch.getFileExtension();
    }

    private static void extractMacBinary(Path binariesFolder, String binaryName) {
        ProcessBuilder processBuilder = new ProcessBuilder(
            "tar", "-xvzf",
            Paths.get(binariesFolder.toString(), binaryName).toString(),
            "-C", binariesFolder.toString()
        );
        try {
            Process process = processBuilder.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            Constants.LOG.error("Failed to extract Mac binary", e);
        }
    }
    private static Path findExistingBinary(Path binariesFolder) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(binariesFolder)) {
            for (Path p : stream) {
                String name = p.getFileName().toString().toLowerCase();
                if (name.startsWith(Constants.CLOUDFLARED_BINARY_NAME.toLowerCase())) {
                    // Prefer an actual file (not directory)
                    if (Files.isRegularFile(p)) {
                        return p;
                    }
                }
            }
        } catch (IOException e) {
            Constants.LOG.debug("Error while searching for existing binary", e);
        }
        return null;
    }
    private static void setExecutableIfNeeded(Path p, Platform osArch) {
        if (p == null) return;
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return; // no posix perms on Windows

        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
            Files.setPosixFilePermissions(p, perms);
        } catch (UnsupportedOperationException | IOException e) {
            Constants.LOG.debug("Unable to set executable permission for {}", p, e);
        }
    }
    public Path getCloudflaredDirectory() {
        String userHome = getUserHome();
        String os = System.getProperty("os.name").toLowerCase();
        Path[] pathLocations = getCloudflaredPaths(userHome, os);

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

    private static String getUserHome() {
        String userHome = System.getenv("USERPROFILE");
        if (userHome == null || userHome.isEmpty()) {
            userHome = System.getProperty("user.home");
        }
        return userHome;
    }

    private static Path[] getCloudflaredPaths(String userHome, String os) {
        Path userhomePath = Paths.get(userHome, Constants.CLOUDFLARED_DIR_NAME);

        if (os.contains("win")) {
            return new Path[]{userhomePath};
        } else {
            return new Path[]{
                userhomePath,
                Paths.get("/etc", Constants.CONFIG_DIR),
                Paths.get("/usr/local/etc", Constants.CONFIG_DIR)
            };
        }
    }
    public boolean isAuthenticated() {
        Path cloudflaredDir = getCloudflaredDirectory();
        if (cloudflaredDir != null) {
            Path certFile = cloudflaredDir.resolve(Constants.CLOUDFLARED_CERT_FILE);
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
            Process process = processHandler.runAsync(new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_LOGIN), null, null);
            // Have it separately handle the output to catch the URL and check if authentication ends.
            Thread processThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;

                    while ((line = reader.readLine()) != null) {
                        Matcher m = URL_REGEX.matcher(line);
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

    public boolean validateTunnelExist(TunnelInfo info) {
        if(isDefaultTunnelUUID(info.getId())) return false;
        // Try with name first then id
        boolean exists = validateTunnelExist(info.getName());
        if (!exists) {
            exists = validateTunnelExist(info.getId());
        }
        return exists;
    }
    public boolean validateTunnelExist(String tunnelIdOrName) {
        AtomicBoolean doesExist = new AtomicBoolean(false);
        Constants.LOG.info(tunnelIdOrName);
        try {
            Process process = processHandler.runAsync(new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_INFO, tunnelIdOrName), output -> {
                if (isTunnelNotFound(output)) {
                    tunnelNotFound(tunnelIdOrName);
                    doesExist.set(false);
                } else {
                    doesExist.set(true);
                }
            }, Constants.LOG::error);
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            Constants.LOG.error("error", e);
        }
        return doesExist.get();
    }
    /*
    public DetailedTunnelInfo getDetailedTunnelInfo(TunnelInfo tunnel) {
        if(isDefaultTunnelUUID(tunnel.getId())) return null;
        // try with name first then id
        TunnelInfo info = getTunnelInfo(tunnel.getName());
        if(info == null) {
            if(tunnel.getId().equals(new UUID(0,0).toString())) return null;
            info = getTunnelInfo(tunnel.getId());
            if (info == null) {
                tunnelNotFound(tunnel.getId());
                return null;
            }
        }
        return info;
    }
    public DetailedTunnelInfo getDetailedTunnelInfo(String tunnelIdOrName) {
        TunnelInfo info = new TunnelInfo();
        try {
            Process process = processHandler.executeCommandAsync(new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_INFO, tunnelIdOrName));
            processHandler.captureProcessOutput(process, line -> {
                Matcher matcher = TUNNEL_INFO_REGEX.matcher(line.trim());
                Constants.LOG.info(line);

                if (isTunnelNotFound(line)) {
                   tunnelNotFound(tunnelIdOrName);;
                    return null;
                }
                if (matcher.find()) {
                    if (matcher.group(1) != null) {
                        info.setName(matcher.group(1));
                    } else if (matcher.group(2) != null) {
                        info.setId(matcher.group(2));
                        // TODO: Handle created date if needed
                        // TODO: handle connector data
                    } else if (matcher.group(3) != null) {
                        try {
                            info.setCreated(Date.from(Instant.parse(matcher.group(3))));
                            Constants.LOG.info(matcher.group(3));
                        } catch (DateTimeParseException e) {
                            info.setCreated(null);
                            System.err.println("Invalid date format: " + matcher.group(3));
                        }
                     }
                }
                return Optional.empty();
            });
        } catch (IOException e) {
            Constants.LOG.error("error", e);
        }
        return info;
    }
    */
    public TunnelInfo getTunnelInfo(TunnelInfo tunnel) {
        if (tunnel == null) {
            Constants.LOG.debug("getTunnelInfo called with null tunnel");
            return null;
        }
        Constants.LOG.debug("getTunnelInfo called for id={} name={}", tunnel.getId(), tunnel.getName());
        // try with name first then id
        TunnelInfo info = getTunnelInfo(tunnel.getName());
        if (info == null) {
            Constants.LOG.debug("No tunnel found by name='{}', trying id='{}'", tunnel.getName(), tunnel.getId());
            if (isDefaultTunnelUUID(tunnel.getId())) {
                Constants.LOG.debug("Tunnel id equals zero UUID, returning null");
                return null;
            }
            info = getTunnelInfo(tunnel.getId());
            if (info == null) {
                tunnelNotFound(tunnel.getId());
                Constants.LOG.debug("No tunnel found by id='{}', returning null", tunnel.getId());
                return null;
            }
        } else {
            Constants.LOG.debug("Tunnel found by name='{}'", tunnel.getName());
        }
        Constants.LOG.debug("Returning tunnel info: {}", info);
        return info;
    }
    public TunnelInfo getTunnelInfo(String tunnelIdOrName) {
        AtomicReference<TunnelInfo> info = new AtomicReference<>(null);
        try {
            Process process = processHandler.runAsync(
                new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_LIST),
                line -> {
                    Matcher matcher = TUNNEL_INFO_REGEX.matcher(line.trim());
                    if (isTunnelNotFound(line)) {
                        tunnelNotFound(tunnelIdOrName);
                        info.set(null);
                    }
                    if (matcher.find()) {
                        if (matcher.group(1).equals(tunnelIdOrName) || matcher.group(2).equals(tunnelIdOrName)) {
                            TunnelInfo tunnelInfo = new TunnelInfo();
                            tunnelInfo.setId(matcher.group(1));
                            tunnelInfo.setName(matcher.group(2));
                            info.set(tunnelInfo);
                        }
                    }
                },
                Constants.LOG::error);
                process.waitFor();
        } catch (IOException | InterruptedException e) {
            Constants.LOG.error("error", e);
        }
        return info.get();
    }
    public void runTunnelUsingTokenBackground(String token) {
        try {
            processHandler.runAsync(
                new SubCommand(Constants.CMD_TUNNEL, Constants.CMD_RUN, "--token", token),
                null,
                error -> Constants.LOG.error("Error running tunnel with token: {}", error));
        } catch (IOException e) {
            Constants.LOG.error("Failed to run tunnel in background", e);
        }
    }
    private static boolean isTunnelNotFound(String line) {
        String lowerLine = line.toLowerCase();
        return lowerLine.contains("is neither the id nor the name of any of your tunnels") ||
               lowerLine.contains("but found 0 tunnels");
    }
    public String getBinaryPath() {
        return binaryPath;
    }
    public boolean isDefaultTunnelUUID(String tunnelUUID) {
        return tunnelUUID.equals(new UUID(0,0).toString());
    }
    public void tunnelNotFound(String tunnelIdOrName) {
        Constants.LOG.debug("Tunnel with ID or Name '{}' not found", tunnelIdOrName);
    }
}

