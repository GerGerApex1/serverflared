// File: common/src/main/java/me/gergerapex1/serverflared/cloudflared/handler/CloudflaredProcessHandler.java
package me.gergerapex1.serverflared.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import me.gergerapex1.serverflared.Constants;

public class ProcessHandler {
    private final String binaryPath;
    private final List<Process> processes = new ArrayList<>();
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    public ProcessHandler(String binaryPath) {
        this.binaryPath = binaryPath;
    }

    public Process run(SubCommand subCommand, Consumer<String> stdout, Consumer<String> stderr) {
        try {
            Process process = createProcess(subCommand);
            processes.add(process);
            EXECUTOR_SERVICE.submit(() -> captureProcessOutput(process.getInputStream(), stdout));
            EXECUTOR_SERVICE.submit(() -> captureProcessOutput(process.getErrorStream(), stderr));


            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Constants.LOG.error("Command failed with exit code: {}", exitCode);
            }
            return process;
        } catch (IOException | InterruptedException e) {
            Constants.LOG.error("Failed to execute command", e);
            return null;
        }
    }
    /*
    public <R> Optional<R> captureProcessOutput(Process process, Function<String, Optional<R>> onLine) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Optional<R> maybe = onLine.apply(line);
                if(process.info().commandLine().isPresent()) {
                    Constants.LOG.debug("({}) {}", process.info().commandLine().get(), line);
                }
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
     */
    public Process runAsync(SubCommand subCommand, Consumer<String> stdout, Consumer<String> stderr) throws IOException {
        Process process = createProcess(subCommand);
        EXECUTOR_SERVICE.submit(() -> captureProcessOutput(process.getInputStream(), stdout));
        EXECUTOR_SERVICE.submit(() -> captureProcessOutput(process.getErrorStream(), stderr));
        processes.add(process);
        return process;
    }
    private static void captureProcessOutput(InputStream inputStream, Consumer<String> cb) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Constants.LOG.debug(line);
                cb.accept(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private Process createProcess(SubCommand subCommand) throws IOException {
        List<String> cmdList = new ArrayList<>(subCommand.getCommandList());
        // Prepend binary path in a Java 8-safe way
        cmdList.add(0, binaryPath);
        Constants.LOG.debug("Executing: {}", String.join(" ", cmdList));

        ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
        processBuilder.redirectErrorStream(true);
        return processBuilder.start();
    }

    public void terminate() {
        for (Process p : processes) {
            try {
                p.destroy();
				boolean isExited = p.waitFor(3, TimeUnit.SECONDS);
				if(!isExited) {
					p.destroyForcibly();
					p.waitFor(2, TimeUnit.SECONDS);
				}
                Constants.LOG.info("Terminated process: {}", p.exitValue());
            } catch (Exception e) {
                Constants.LOG.warn("Failed to terminate process: {}", p);
            }
        }
        processes.clear();
    }
}
