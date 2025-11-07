// File: common/src/main/java/me/gergerapex1/servergotflared/cloudflared/handler/CloudFlaredProcessExecutor.java
package me.gergerapex1.serverflared.cloudflared.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.gergerapex1.serverflared.Constants;

public class CloudflaredProcessHandler {
    private final String binaryPath;
    private final ArrayList<Long> pids = new ArrayList<>();
    public CloudflaredProcessHandler(String binaryPath) {
        this.binaryPath = binaryPath;
    }
    public int executeCommand(String... command) {
        try {
            List<String> cmdList = new ArrayList<>(Arrays.asList(command));
            cmdList.addFirst(binaryPath);
            Constants.LOG.debug("Executing: {}", String.join(" ", cmdList));

            ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            pids.add(process.pid());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Constants.LOG.debug(line);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Constants.LOG.error("Command failed with exit code: {}", exitCode);
            }
            return exitCode;
        } catch (IOException | InterruptedException e) {
            Constants.LOG.error("Failed to execute command", e);
            return 1;
        }
    }

    public Process executeCommandAsync(String... command) throws IOException {
        List<String> cmdList = new ArrayList<>(Arrays.asList(command));
        cmdList.addFirst(binaryPath);

        ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        pids.add(process.pid());
        return process;
    }
    public void terminate() {
        for(Long pid : pids) {
            ProcessHandle.of(pid).ifPresent(ProcessHandle::destroy);
            Constants.LOG.info("Terminated cloudflared process with PID: {}", pid);
        }
        pids.clear();
    }
}
