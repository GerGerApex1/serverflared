package me.gergerapex1.serverflared.cloudflared.binaries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import me.gergerapex1.serverflared.Constants;

public class BinaryHandler {
    public static boolean checkCloudflaredVersion(String path) {
        try {
            Process process = new ProcessBuilder(path, "--version").redirectErrorStream(true).start();
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                Constants.LOG.debug("cloudflared version: " + line);
                if(line.toLowerCase().contains("cloudflared")) {
                    return true;
                }
            }
        } catch (IOException e) {
            Constants.LOG.error(e);
            return false;
        }
        return false;
    }
    public static boolean binaryExistInPath() {
        // find the binary in the system path first
        String findCommand = "";
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            findCommand = "where";
        } else {
            findCommand = "which";
        }
        try {
            Process process = new ProcessBuilder(findCommand, "cloudflared").redirectErrorStream(true).start();
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                if(findCommand.equals("where")) {
                    Constants.LOG.debug("its windows, we are using where command");
                    if(line.toLowerCase().endsWith("cloudflared.exe")) {
                        return true;
                    }
                } else {
                    Constants.LOG.debug("its linux/mac, we are using which command");
                    if(line.equals("cloudflared")) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            Constants.LOG.error(e);
            return false;
        }
        return false;
    }
}
