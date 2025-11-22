package me.gergerapex1.serverflared.cloudflared.binaries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import me.gergerapex1.serverflared.Constants;

public class BinaryHandler {
    private static final String WINDOWS_FIND_COMMAND = "where";
    private static final String UNIX_FIND_COMMAND = "which";
    private static final String CLOUDFLARED_EXE = "cloudflared.exe";
    
    public static boolean checkCloudflaredVersion(String path) {
        try {
            Process process = new ProcessBuilder(path, "--version")
                .redirectErrorStream(true)
                .start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Constants.LOG.debug("cloudflared version: {}", line);
                    if (line.toLowerCase().contains(Constants.CLOUDFLARED_BINARY_NAME)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            Constants.LOG.error("Failed to check cloudflared version", e);
        }
        return false;
    }
    
    public static boolean binaryExistInPath() {
        String findCommand = isWindows() ? WINDOWS_FIND_COMMAND : UNIX_FIND_COMMAND;
        
        try {
            Process process = new ProcessBuilder(findCommand, Constants.CLOUDFLARED_BINARY_NAME)
                .redirectErrorStream(true)
                .start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (isValidCloudflaredPath(line, findCommand)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            Constants.LOG.error("Failed to check if binary exists in PATH", e);
        }
        return false;
    }
    
    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
    
    private static boolean isValidCloudflaredPath(String path, String findCommand) {
        if (WINDOWS_FIND_COMMAND.equals(findCommand)) {
            Constants.LOG.debug("Windows system, using where command");
            return path.toLowerCase().endsWith(CLOUDFLARED_EXE);
        } else {
            Constants.LOG.debug("Linux/Mac system, using which command");
            return path.equals(Constants.CLOUDFLARED_BINARY_NAME);
        }
    }
}
