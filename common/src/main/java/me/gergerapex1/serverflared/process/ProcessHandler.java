// File: common/src/main/java/me/gergerapex1/serverflared/cloudflared/handler/CloudflaredProcessHandler.java
package me.gergerapex1.serverflared.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import me.gergerapex1.serverflared.Constants;

public class ProcessHandler {
    private final String binaryPath;
    private final ArrayList<Long> pids = new ArrayList<>();
    
    public ProcessHandler(String binaryPath) {
        this.binaryPath = binaryPath;
    }
    
    public int executeCommand(SubCommand subCommand) {
        try {
            Process process = startProcess(subCommand);
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
    public <R> Optional<R> captureProcessOutput(Process process, Function<String, Optional<R>> onLine) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Optional<R> maybe = onLine.apply(line);
                if (maybe != null && maybe.isPresent()) {
                    process.destroy();
                    process.waitFor();
                    return maybe;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }
    public Process executeCommandAsync(SubCommand subCommand) throws IOException {
        Process process = startProcess(subCommand);
        pids.add(process.pid());
        return process;
    }
    
    private Process startProcess(SubCommand subCommand) throws IOException {
        List<String> cmdList = subCommand.getCommandList();
        cmdList.addFirst(binaryPath);
        Constants.LOG.debug("Executing: {}", String.join(" ", cmdList));
        
        ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
        processBuilder.redirectErrorStream(true);
        return processBuilder.start();
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
            Constants.LOG.info("Terminated processes with PID: {}", pid);
        }
        pids.clear();
    }
}
