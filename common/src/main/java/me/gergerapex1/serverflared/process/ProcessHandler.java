// File: common/src/main/java/me/gergerapex1/serverflared/cloudflared/handler/CloudflaredProcessHandler.java
package me.gergerapex1.serverflared.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import me.gergerapex1.serverflared.Constants;

public class ProcessHandler {
    public static final long DEFAULT_COMMAND_TIMEOUT_SECONDS = 30L;
    public static final int DEFAULT_MAX_CAPTURE_LINES = 4096;
    public static final int DEFAULT_MAX_CAPTURE_BYTES = 1024 * 1024;

    private final String binaryPath;
    private final List<Process> processes = new CopyOnWriteArrayList<>();
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "serverflared-process-io-" + THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    public ProcessHandler(String binaryPath) {
        this.binaryPath = binaryPath;
    }

    public Process run(SubCommand subCommand, Consumer<String> stdout, Consumer<String> stderr) {
        return run(subCommand, stdout, stderr, DEFAULT_COMMAND_TIMEOUT_SECONDS);
    }

    public Process run(SubCommand subCommand, Consumer<String> stdout, Consumer<String> stderr, long timeoutSeconds) {
        Process process = null;
        Future<?> stdoutFuture = null;
        Future<?> stderrFuture = null;
        try {
            process = createProcess(subCommand);
            processes.add(process);
            final InputStream stdoutStream = process.getInputStream();
            final InputStream stderrStream = process.getErrorStream();
            stdoutFuture = EXECUTOR_SERVICE.submit(
                () -> captureProcessOutput(stdoutStream, stdout, DEFAULT_MAX_CAPTURE_LINES, DEFAULT_MAX_CAPTURE_BYTES)
            );
            stderrFuture = EXECUTOR_SERVICE.submit(
                () -> captureProcessOutput(stderrStream, stderr, DEFAULT_MAX_CAPTURE_LINES, DEFAULT_MAX_CAPTURE_BYTES)
            );

            long effectiveTimeout = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_COMMAND_TIMEOUT_SECONDS;
            boolean finished = process.waitFor(effectiveTimeout, TimeUnit.SECONDS);
            if (!finished) {
                Constants.LOG.error("Command timed out after {} seconds; terminating process.", effectiveTimeout);
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
                stdoutFuture.cancel(true);
                stderrFuture.cancel(true);
                processes.remove(process);
                return null;
            }

            awaitCapture(stdoutFuture);
            awaitCapture(stderrFuture);
            processes.remove(process);
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                Constants.LOG.error("Command failed with exit code: {}", exitCode);
            }
            return process;
        } catch (IOException e) {
            Constants.LOG.error("Failed to execute command", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
                processes.remove(process);
            }
            if (stdoutFuture != null) {
                stdoutFuture.cancel(true);
            }
            if (stderrFuture != null) {
                stderrFuture.cancel(true);
            }
            Constants.LOG.error("Command execution interrupted", e);
            return null;
        }
    }

    private static void awaitCapture(Future<?> future) throws InterruptedException {
        if (future == null) {
            return;
        }
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            Constants.LOG.debug("Timed out waiting for process output capture to finish");
        } catch (ExecutionException e) {
            Constants.LOG.debug("Process output capture failed: {}", e.getMessage());
        }
    }

    public Process runAsync(SubCommand subCommand, Consumer<String> stdout, Consumer<String> stderr) throws IOException {
        return runAsync(subCommand, null, null, stdout, stderr);
    }

    public Process runAsync(SubCommand subCommand, Map<String, String> env, Consumer<String> stdout, Consumer<String> stderr) throws IOException {
        return runAsync(subCommand, env, null, stdout, stderr);
    }

    public Process runAsync(
        SubCommand subCommand,
        Map<String, String> env,
        Collection<String> unsetEnvKeys,
        Consumer<String> stdout,
        Consumer<String> stderr
    ) throws IOException {
        Process process = createProcess(subCommand, env, unsetEnvKeys);
        EXECUTOR_SERVICE.submit(() -> captureProcessOutput(process.getInputStream(), stdout));
        EXECUTOR_SERVICE.submit(() -> captureProcessOutput(process.getErrorStream(), stderr));
        processes.add(process);
        return process;
    }

    private static void captureProcessOutput(InputStream inputStream, Consumer<String> cb) {
        captureProcessOutput(inputStream, cb, Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    private static void captureProcessOutput(InputStream inputStream, Consumer<String> cb, int maxLines, long maxBytes) {
        int lineCount = 0;
        long byteCount = 0L;
        boolean truncated = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                if (!truncated) {
                    lineCount++;
                    byteCount += line.length() + 1L;
                    if (lineCount > maxLines || byteCount > maxBytes) {
                        truncated = true;
                        Constants.LOG.warn("Process output exceeded capture limit ({} lines / {} bytes); truncating further captured lines.",
                            maxLines, maxBytes);
                        continue;
                    }
                    Constants.LOG.debug(line);
                    if (cb != null) {
                        cb.accept(line);
                    }
                }
            }
        } catch (IOException e) {
            Constants.LOG.debug("Process stream closed: {}", e.getMessage());
        }
    }

    private Process createProcess(SubCommand subCommand) throws IOException {
        return createProcess(subCommand, null, null);
    }

    private Process createProcess(SubCommand subCommand, Map<String, String> env, Collection<String> unsetEnvKeys) throws IOException {
        List<String> cmdList = new ArrayList<>(subCommand.getCommandList());
        // Prepend binary path in a Java 8-safe way
        cmdList.add(0, binaryPath);
        Constants.LOG.debug("Executing: {}", formatCommandForLog(cmdList));

        ProcessBuilder processBuilder = new ProcessBuilder(cmdList);
        Map<String, String> processEnv = processBuilder.environment();
        if (unsetEnvKeys != null) {
            for (String key : unsetEnvKeys) {
                if (key != null && !key.isEmpty()) {
                    processEnv.remove(key);
                }
            }
        }
        if (env != null && !env.isEmpty()) {
            processEnv.putAll(env);
        }
        processBuilder.redirectErrorStream(true);
        return processBuilder.start();
    }

    public static String formatCommandForLog(List<String> cmdList) {
        if (cmdList == null || cmdList.isEmpty()) {
            return "";
        }
        List<String> sanitized = new ArrayList<>(cmdList.size());
        boolean maskNext = false;
        for (String arg : cmdList) {
            if (maskNext) {
                sanitized.add("***REDACTED***");
                maskNext = false;
            } else if ("--token".equals(arg) || "-t".equals(arg)) {
                sanitized.add(arg);
                maskNext = true;
            } else if (arg != null && arg.startsWith("--token=")) {
                sanitized.add("--token=***REDACTED***");
            } else {
                sanitized.add(arg);
            }
        }
        return String.join(" ", sanitized);
    }

    public boolean hasRunningProcess() {
        for (Process p : processes) {
            if (p != null && p.isAlive()) {
                return true;
            }
        }
        return false;
    }

    public Process getActiveProcess() {
        for (int i = processes.size() - 1; i >= 0; i--) {
            Process p = processes.get(i);
            if (p != null && p.isAlive()) {
                return p;
            }
        }
        return null;
    }

    public static long getProcessPid(Process process) {
        if (process == null) {
            return -1L;
        }
        try {
            Method pidMethod = Process.class.getMethod("pid");
            Object result = pidMethod.invoke(process);
            if (result instanceof Number) {
                return ((Number) result).longValue();
            }
        } catch (Throwable ignored) {
            // Fallback for Java 8 UNIXProcess
            try {
                Field pidField = process.getClass().getDeclaredField("pid");
                pidField.setAccessible(true);
                return pidField.getLong(process);
            } catch (Throwable ignored2) {
            }
        }
        return -1L;
    }

    public synchronized void terminate() {
        for (Process p : processes) {
            try {
                if (p != null && p.isAlive()) {
                    p.destroy();
                    boolean isExited = p.waitFor(3, TimeUnit.SECONDS);
                    if (!isExited) {
                        p.destroyForcibly();
                        p.waitFor(2, TimeUnit.SECONDS);
                    }
                    Constants.LOG.info("Terminated process: {}", p.exitValue());
                }
            } catch (Exception e) {
                Constants.LOG.warn("Failed to terminate process: {}", p);
            }
        }
        processes.clear();
    }
}
