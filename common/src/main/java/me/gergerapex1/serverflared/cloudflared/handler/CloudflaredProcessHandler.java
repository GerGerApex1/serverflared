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
            Process process = startProcess(command);
            pids.add(process.pid());
            logProcessOutput(process);
            
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
        Process process = startProcess(command);
        pids.add(process.pid());
        return process;
    }
    
    private Process startProcess(String... command) throws IOException {
        List<String> cmdList = buildCommandList(command);
        Constants.LOG.debug("Executing: {}", String.join(" ", cmdList));
        
        ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
        processBuilder.redirectErrorStream(true);
        return processBuilder.start();
    }
    
    private List<String> buildCommandList(String... command) {
        List<String> cmdList = new ArrayList<>(Arrays.asList(command));
        cmdList.addFirst(binaryPath);
        return cmdList;
    }
    
    private void logProcessOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Constants.LOG.debug(line);
            }
        }
    }
    
    public void terminate() {
        for (Long pid : pids) {
            ProcessHandle.of(pid).ifPresent(ProcessHandle::destroy);
            Constants.LOG.info("Terminated cloudflared process with PID: {}", pid);
        }
        pids.clear();
    }
}
