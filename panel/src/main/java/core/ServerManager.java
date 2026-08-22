package core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Modern implementation of server management with Java 21 features.
 * Implements IServerManager interface for better abstraction.
 * Uses sealed classes and records for better type safety.
 */
public final class ServerManager implements IServerManager {

    // Sealed hierarchy for error types
    public sealed interface ServerError {
        record CrashError(String message, long timestamp) implements ServerError {
        }

        record BindError(String address) implements ServerError {
        }

        record OutOfMemoryError(long heapSize) implements ServerError {
        }

        record LogError(String exceptionName, String message, long timestamp) implements ServerError {
        }
    }

    // Record for error details
    public record ErrorEntry(ServerError error, String rawMessage, long timestamp) {
    }

    public record RecentErrorDetail(String summary, String cause, String fix, String rawLine, String title) {
    }

    private volatile Process process;
    private volatile BufferedWriter stdin;
    private volatile Status status = Status.STOPPED;
    private volatile long startTime = 0;

    private final String serverDir;
    private final List<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Status>> statusListeners = new CopyOnWriteArrayList<>();
    private final List<String> onlinePlayers = new CopyOnWriteArrayList<>();
    private final List<ErrorEntry> recentErrorDetails = new CopyOnWriteArrayList<>();

    // Constants
    private static final long TPS_STALE_MS = 35_000L;
    private static final long MAX_PLAYERS_REFRESH_MS = 30_000L;
    private static final int MAX_ERROR_HISTORY = 50;
    private static final long RAM_REFRESH_MS = 10_000L;

    // Patterns
    private static final Pattern HEAP_USED_PATTERN = Pattern.compile("\\bused\\s+(\\d+)([KMG])\\b",
            Pattern.CASE_INSENSITIVE);

    private volatile double tps = 0.0;
    private volatile long lastTpsUpdateMillis = 0L;
    private volatile long configuredMaxRamBytes = -1L;
    private volatile String lastRamSnapshot = "?";
    private volatile ScheduledExecutorService ramRefreshExecutor;
    private volatile String javaCommandPath;
    private volatile String jcmdCommandPath;
    private volatile int cachedMaxPlayers = 20;
    private volatile long lastMaxPlayersReadMillis = 0L;
    private final Object lifecycleLock = new Object();

    // Singleton instance
    private static volatile ServerManager instance;

    /**
     * Private constructor for singleton pattern.
     */
    private ServerManager(String serverDir) {
        this.serverDir = Objects.requireNonNull(serverDir, "serverDir cannot be null");
        registerShutdownHook();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (lifecycleLock) {
                Process p = process;
                if (p != null && p.isAlive()) {
                    try {
                        sendCommand("stop");
                        p.waitFor(10, TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                    }
                }
            }
        }, "ServerManagerShutdownHook"));
    }

    /**
     * Singleton factory method with double-checked locking.
     */
    public static synchronized ServerManager getInstance(String serverDir) {
        String resolvedServerDir = Objects.requireNonNull(serverDir, "serverDir cannot be null");
        if (instance == null) {
            instance = new ServerManager(resolvedServerDir);
        } else if (!instance.serverDir.equals(resolvedServerDir)) {
            throw new IllegalStateException(
                    "ServerManager zaten '" + instance.serverDir + "' için başlatılmış. Yeni istek: '"
                            + resolvedServerDir + "'");
        }
        return instance;
    }

    /**
     * Resets the singleton instance (for testing).
     */
    static synchronized void resetInstance() {
        instance = null;
    }

    @Override
    public void start() {
        synchronized (lifecycleLock) {
            if (status != Status.STOPPED) {
                fireLog("[Panel] " + Lang.get("ERR_ALREADY_STARTING"));
                return;
            }
            setStatus(Status.STARTING);
            onlinePlayers.clear();
            recentErrorDetails.clear();
            tps = 0.0;
            lastTpsUpdateMillis = 0L;
            configuredMaxRamBytes = -1L;
            lastRamSnapshot = "?";
            javaCommandPath = null;
            jcmdCommandPath = null;
            lastMaxPlayersReadMillis = 0L;

            try {
                List<String> command = parseStartCommand();
                if (command == null || command.isEmpty()) {
                    fireLog("[Panel] " + Lang.get("ERR_BAT_NOT_FOUND"));
                    setStatus(Status.STOPPED);
                    return;
                }

                String executable = command.getFirst();
                if (executable.startsWith(".\\") || executable.startsWith("./")) {
                    executable = new File(serverDir, executable).getAbsolutePath();
                    command.set(0, executable);
                }

                configuredMaxRamBytes = extractMaxRamBytes(command);

                // Script doğrudan çalıştırılıyorsa (cmd.exe/bash) java yolu bilinmiyor;
                // jcmd bulunamaz, RAM gösterimi "?" kalır — bu beklenen davranış.
                boolean isDirectScript = executable.equalsIgnoreCase("cmd.exe") || executable.equalsIgnoreCase("bash");
                javaCommandPath = isDirectScript ? null : executable;
                jcmdCommandPath = isDirectScript ? null : resolveJcmdCommandPath(javaCommandPath);

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.directory(new File(serverDir));
                processBuilder.redirectErrorStream(true);

                Process startedProcess = processBuilder.start();
                BufferedWriter startedStdin = new BufferedWriter(new OutputStreamWriter(startedProcess.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));

                process = startedProcess;
                stdin = startedStdin;

                startRamMonitor();
                startOutputReader(startedProcess, startedStdin);
            } catch (IOException e) {
                fireLog("[Panel] " + Lang.get("ERR_START_FAIL") + ": " + e.getMessage());
                registerError(new ServerError.CrashError(e.getMessage(), System.currentTimeMillis()));
                setStatus(Status.STOPPED);
                closeServerResources();
            }
        }
    }

    @Override
    public void stop() {
        synchronized (lifecycleLock) {
            if (status != Status.RUNNING && status != Status.STARTING) {
                return;
            }
            setStatus(Status.STOPPING);
        }

        sendCommand("stop");

        // Force kill after 30 seconds
        Process p = process;
        CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS)
                .execute(() -> {
                    if (p != null && p.isAlive()) {
                        fireLog("[Panel] " + Lang.get("LOG_FORCE_KILL"));
                        p.destroyForcibly();
                    }
                });
    }

    @Override
    public void restart() {
        if (status == Status.RUNNING) {
            // Wait for actual STOPPED status before starting again
            Consumer<Status> restartListener = new Consumer<>() {
                @Override
                public void accept(Status newStatus) {
                    if (newStatus == Status.STOPPED) {
                        removeStatusListener(this);
                        // Small delay to let resources clean up
                        CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS)
                                .execute(ServerManager.this::start);
                    }
                }
            };
            addStatusListener(restartListener);
            stop();

            // Safety net: if server doesn't stop within 60 seconds, remove listener
            CompletableFuture.delayedExecutor(60, TimeUnit.SECONDS)
                    .execute(() -> removeStatusListener(restartListener));
        } else if (status == Status.STOPPED) {
            start();
        }
    }

    @Override
    public void sendCommand(String command) {
        BufferedWriter bw = stdin;
        Process p = process;
        if (bw == null || p == null || !p.isAlive()) {
            return;
        }

        try {
            bw.write(command);
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            fireLog("[Panel] " + Lang.get("ERR_CMD_SEND") + ": " + e.getMessage());
        }
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public List<String> getOnlinePlayers() {
        return List.copyOf(onlinePlayers);
    }

    @Override
    public List<String> getRecentErrors() {
        return recentErrorDetails.stream()
                .map(ErrorEntry::rawMessage)
                .limit(MAX_ERROR_HISTORY)
                .toList();
    }

    @Override
    public double getTps() {
        if (System.currentTimeMillis() - lastTpsUpdateMillis > TPS_STALE_MS) {
            return 0.0;
        }
        return tps;
    }

    @Override
    public String getRamUsage() {
        return lastRamSnapshot;
    }

    @Override
    public String getUptime() {
        if (startTime == 0) {
            return "00:00:00";
        }
        long uptimeMillis = System.currentTimeMillis() - startTime;
        return formatDuration(uptimeMillis);
    }

    @Override
    public int getMaxPlayers() {
        long now = System.currentTimeMillis();
        if (now - lastMaxPlayersReadMillis > MAX_PLAYERS_REFRESH_MS) {
            cachedMaxPlayers = readMaxPlayersFromConfig();
            lastMaxPlayersReadMillis = now;
        }
        return cachedMaxPlayers;
    }

    @Override
    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    @Override
    public boolean isStopped() {
        return status == Status.STOPPED;
    }

    @Override
    public boolean isStarting() {
        return status == Status.STARTING;
    }

    @Override
    public boolean isStopping() {
        return status == Status.STOPPING;
    }

    @Override
    public void addLogListener(Consumer<String> listener) {
        logListeners.add(listener);
    }

    @Override
    public void removeLogListener(Consumer<String> listener) {
        logListeners.remove(listener);
    }

    @Override
    public void addStatusListener(Consumer<Status> listener) {
        statusListeners.add(listener);
    }

    @Override
    public void removeStatusListener(Consumer<Status> listener) {
        statusListeners.remove(listener);
    }

    // Private helper methods

    private void setStatus(Status newStatus) {
        this.status = newStatus;
        if (newStatus == Status.RUNNING) {
            startTime = System.currentTimeMillis();
        } else if (newStatus == Status.STOPPED) {
            startTime = 0L;
        }
        for (Consumer<Status> listener : statusListeners) {
            try {
                listener.accept(newStatus);
            } catch (RuntimeException e) {
                fireLog("[Panel] Status listener error: " + e.getMessage());
            }
        }
    }

    private void fireLog(String message) {
        for (Consumer<String> listener : logListeners) {
            try {
                listener.accept(message);
            } catch (RuntimeException ignored) {
                // A broken UI listener should not break server management.
            }
        }
    }

    private void registerError(ServerError error) {
        String rawMessage = switch (error) {
            case ServerError.CrashError crash -> "Server crash: " + crash.message();
            case ServerError.BindError bind -> "Port already in use: " + bind.address();
            case ServerError.OutOfMemoryError oom -> "Out of memory: " + oom.heapSize() + " bytes";
            case ServerError.LogError logErr -> "Logged Error: [" + logErr.exceptionName() + "] " + logErr.message();
        };

        ErrorEntry entry = new ErrorEntry(error, rawMessage, System.currentTimeMillis());
        recentErrorDetails.addFirst(entry);

        // Keep only recent errors
        if (recentErrorDetails.size() > MAX_ERROR_HISTORY) {
            recentErrorDetails.removeLast();
        }
    }

    private void startRamMonitor() {
        stopRamMonitor();

        ramRefreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RamMonitor");
            t.setDaemon(true);
            return t;
        });

        ramRefreshExecutor.scheduleAtFixedRate(
                this::refreshRam,
                2_000L,
                RAM_REFRESH_MS,
                TimeUnit.MILLISECONDS);
    }

    private void startOutputReader(Process activeProcess, BufferedWriter activeStdin) {
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(activeProcess.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {

                String line;
                while ((line = br.readLine()) != null) {
                    processLogLine(line);
                }

            } catch (IOException e) {
                if (status != Status.STOPPING && status != Status.STOPPED) {
                    fireLog("[Panel] " + Lang.get("ERR_READ") + ": " + e.getMessage());
                }
            } finally {
                closeServerResources(activeProcess, activeStdin);

                // Handle process termination
                if (status == Status.STOPPING) {
                    setStatus(Status.STOPPED);
                    fireLog("[Panel] " + Lang.get("LOG_SERVER_STOPPED"));
                } else if (status == Status.RUNNING || status == Status.STARTING) {
                    setStatus(Status.STOPPED);
                    fireLog("[Panel] " + Lang.get("LOG_SERVER_CRASH"));
                    registerError(new ServerError.CrashError("Unexpected termination", System.currentTimeMillis()));
                }
            }
        }, "ServerOutputReader");

        reader.setDaemon(true);
        reader.start();
    }

    private void processLogLine(String rawLine) {
        if (rawLine == null) return;
        String line = rawLine.replaceAll("(?:\\u001B|\\033)\\[[;0-9]*[a-zA-Z]", "");
        // Process log line and update state
        fireLog(line);

        // Detect STARTING → RUNNING transition
        // Minecraft servers print "Done (X.XXXs)! For help, type \"help\""
        // when they finish booting. This is the authoritative signal.
        if (status == Status.STARTING && line.contains("Done") && line.contains("For help")) {
            setStatus(Status.RUNNING);
            fireLog("[Panel] " + Lang.get("LOG_SERVER_STARTED"));
        }

        // Extract player joins/leaves
        String playerJoined = LogAnalyzer.extractPlayerJoined(line);
        if (playerJoined != null) {
            onlinePlayers.add(playerJoined);
        }

        String playerLeft = LogAnalyzer.extractPlayerLeft(line);
        if (playerLeft != null) {
            onlinePlayers.remove(playerLeft);
        }

        // Extract TPS
        Double extractedTps = LogAnalyzer.extractTps(line);
        if (extractedTps != null) {
            tps = extractedTps;
            lastTpsUpdateMillis = System.currentTimeMillis();
        }

        // Analyze errors — classify as LogError instead of CrashError
        LogAnalyzer.ErrorAnalysis error = LogAnalyzer.analyzeError(line);
        if (error != null && error.severity().ordinal() >= LogAnalyzer.ErrorAnalysis.Severity.ERROR.ordinal()) {
            registerError(new ServerError.LogError(error.exceptionName(), error.message(), System.currentTimeMillis()));
        }
    }

    private List<String> parseStartCommand() {
        String[] candidateScripts = {"sunucu_baslat.bat", "start.bat", "run.bat", "launch.bat", "sunucu_baslat.sh", "start.sh", "run.sh"};
        File scriptFile = null;

        for (String scriptName : candidateScripts) {
            File f = new File(serverDir, scriptName);
            if (f.exists()) {
                scriptFile = f;
                break;
            }
        }

        if (scriptFile != null) {
            try {
                List<String> lines = readAllLinesWithFallback(scriptFile.toPath());
                for (String rawLine : lines) {
                    String line = rawLine.trim();
                    if (line.isEmpty() || line.startsWith("REM") || line.startsWith("::") || line.startsWith("@") || line.startsWith("#")) {
                        continue;
                    }
                    if (line.contains("java.exe") || line.contains("java ") || line.contains("-jar") || line.startsWith("java")) {
                        List<String> parsed = parseCommandLine(line);

                        // Forge/NeoForge: run.bat'teki java satırı argfile referanslari içerir
                        // (örn. "java @user_jvm_args.txt @libraries/.../win_args.txt").
                        // Bu token'lar ProcessBuilder'a doğrudan geçilemez; scripti
                        // cmd /c veya bash ile doğrudan çalıştırmak gerekir.
                        boolean hasArgfileTokens = parsed.stream()
                                .skip(1) // java executable'ı atla
                                .anyMatch(t -> t.startsWith("@"));

                        if (hasArgfileTokens) {
                            fireLog("[Panel] Forge/NeoForge argfile başlatma scripti algılandı: " + scriptFile.getName());
                            // -Xmx'i yine de okumaya çalış (RAM gösterimi için)
                            tryExtractMaxRamFromLines(lines);
                            return buildDirectScriptCommand(scriptFile);
                        }

                        return parsed;
                    }
                }

                // Script bulundu ama parse edilebilir java satırı yok
                // (örn. sadece environment ayarlayan wrapper script)
                fireLog("[Panel] Script içinde java komutu bulunamadı, script doğrudan çalıştırılıyor: " + scriptFile.getName());
                return buildDirectScriptCommand(scriptFile);

            } catch (IOException e) {
                fireLog("[Panel] " + Lang.get("ERR_BAT_READ") + ": " + e.getMessage());
            }
        }

        // Fallback: Check for server JAR files in serverDir
        String[] candidateJars = {"purpur.jar", "paper.jar", "spigot.jar", "server.jar", "fabric-server.jar", "forge.jar"};
        String foundJar = null;
        for (String jarName : candidateJars) {
            File jf = new File(serverDir, jarName);
            if (jf.exists() && jf.isFile()) {
                foundJar = jarName;
                break;
            }
        }

        if (foundJar == null) {
            File dir = new File(serverDir);
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
            if (files != null) {
                for (File f : files) {
                    String name = f.getName().toLowerCase();
                    if (!name.startsWith("panel") && !name.contains("plugin")) {
                        foundJar = f.getName();
                        break;
                    }
                }
            }
        }

        if (foundJar != null) {
            // Find best java executable
            File localJava1 = new File(serverDir, "java/bin/java.exe");
            File localJava2 = new File(serverDir, "../java/bin/java.exe");
            String javaPath = "java";
            if (localJava1.exists()) {
                javaPath = localJava1.getAbsolutePath();
            } else if (localJava2.exists()) {
                javaPath = localJava2.getAbsolutePath();
            }

            List<String> autoCommand = new ArrayList<>();
            autoCommand.add(javaPath);
            autoCommand.add("-Xmx2G");
            autoCommand.add("-jar");
            autoCommand.add(foundJar);
            autoCommand.add("nogui");

            // Auto-create sunucu_baslat.bat for user convenience
            File defaultBat = new File(serverDir, "sunucu_baslat.bat");
            if (!defaultBat.exists()) {
                try {
                    String scriptText = "@echo off\r\n\"" + javaPath + "\" -Xmx2G -jar " + foundJar + " nogui\r\npause\r\n";
                    Files.writeString(defaultBat.toPath(), scriptText, java.nio.charset.StandardCharsets.UTF_8);
                    fireLog("[Panel] sunucu_baslat.bat otomatik oluşturuldu.");
                } catch (IOException ignored) {
                }
            }

            return autoCommand;
        }

        return List.of();
    }

    /**
     * Forge/NeoForge argfile başlatma scriptleri için: script satırlarından
     * -Xmx değerini bulmaya çalışır ve configuredMaxRamBytes'ı ayarlar.
     * Bulunamazsa mevcut değer korunur (-1).
     */
    private void tryExtractMaxRamFromLines(List<String> scriptLines) {
        java.util.regex.Pattern xmxPattern = java.util.regex.Pattern.compile("-Xmx(\\d+[KkMmGg])");
        for (String rawLine : scriptLines) {
            java.util.regex.Matcher m = xmxPattern.matcher(rawLine);
            if (m.find()) {
                long bytes = parseMemorySize(m.group(1));
                if (bytes > 0) {
                    configuredMaxRamBytes = bytes;
                }
                return;
            }
        }
        // Forge kullanıcı argfile'ına -Xmx koyabilir; user_jvm_args.txt dene
        File userJvmArgs = new File(serverDir, "user_jvm_args.txt");
        if (userJvmArgs.exists()) {
            try {
                List<String> argLines = readAllLinesWithFallback(userJvmArgs.toPath());
                for (String line : argLines) {
                    java.util.regex.Matcher m = xmxPattern.matcher(line.trim());
                    if (m.find()) {
                        long bytes = parseMemorySize(m.group(1));
                        if (bytes > 0) {
                            configuredMaxRamBytes = bytes;
                        }
                        return;
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Forge/NeoForge gibi argfile tabanlı scriptler için doğrudan çalıştırma komutu.
     * Windows: cmd.exe /c script.bat
     * Unix:    bash script.sh
     */
    private List<String> buildDirectScriptCommand(File script) {
        String name = script.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".bat") || name.endsWith(".cmd")) {
            return List.of("cmd.exe", "/c", script.getAbsolutePath());
        } else {
            return List.of("bash", script.getAbsolutePath());
        }
    }

    private static List<String> readAllLinesWithFallback(Path path) throws IOException {
        try {
            return Files.readAllLines(path, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            try {
                return Files.readAllLines(path, java.nio.charset.StandardCharsets.ISO_8859_1);
            } catch (IOException ex) {
                return Files.readAllLines(path, java.nio.charset.Charset.defaultCharset());
            }
        }
    }

    /**
     * Quote-aware command line parser.
     * Handles paths with spaces enclosed in double quotes.
     * Example: '"G:\Mc sunucu\java\bin\java.exe" -Xmx4G -jar server.jar'
     *        → ["G:\Mc sunucu\java\bin\java.exe", "-Xmx4G", "-jar", "server.jar"]
     */
    private static List<String> parseCommandLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
                // Don't add the quote character itself
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private long extractMaxRamBytes(List<String> command) {
        for (int i = 0; i < command.size(); i++) {
            String arg = command.get(i);
            if (arg.equals("-Xmx") && i + 1 < command.size()) {
                return parseMemorySize(command.get(i + 1));
            } else if (arg.startsWith("-Xmx") && arg.length() > 4) {
                return parseMemorySize(arg.substring(4));
            }
        }
        return -1L;
    }

    private long parseMemorySize(String size) {
        if (size == null || size.isEmpty())
            return -1L;

        try {
            char unit = size.charAt(size.length() - 1);
            if (!Character.isLetter(unit)) {
                // No unit suffix — treat entire string as bytes
                return Long.parseLong(size);
            }
            long value = Long.parseLong(size.substring(0, size.length() - 1));

            return switch (Character.toUpperCase(unit)) {
                case 'K' -> value * 1024L;
                case 'M' -> value * 1024L * 1024L;
                case 'G' -> value * 1024L * 1024L * 1024L;
                default -> value;
            };
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private String resolveJcmdCommandPath(String javaPath) {
        if (javaPath != null && !javaPath.isBlank()) {
            File javaFile = new File(javaPath);
            File binDir = javaFile.getParentFile();
            if (binDir != null) {
                File jcmdFile = new File(binDir, "jcmd");
                if (jcmdFile.exists() && jcmdFile.canExecute()) {
                    return jcmdFile.getAbsolutePath();
                }

                // Try with .exe extension on Windows
                File jcmdExe = new File(binDir, "jcmd.exe");
                if (jcmdExe.exists() && jcmdExe.canExecute()) {
                    return jcmdExe.getAbsolutePath();
                }
            }
        }

        // Fallback 1: Check JAVA_HOME environment variable
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            File bin = new File(javaHome, "bin");
            File jcmdFile = new File(bin, "jcmd");
            if (jcmdFile.exists() && jcmdFile.canExecute()) {
                return jcmdFile.getAbsolutePath();
            }
            File jcmdExe = new File(bin, "jcmd.exe");
            if (jcmdExe.exists() && jcmdExe.canExecute()) {
                return jcmdExe.getAbsolutePath();
            }
        }

        // Fallback 2: Check java.home system property
        String sysJavaHome = System.getProperty("java.home");
        if (sysJavaHome != null && !sysJavaHome.isBlank()) {
            File bin = new File(sysJavaHome, "bin");
            File jcmdFile = new File(bin, "jcmd");
            if (jcmdFile.exists() && jcmdFile.canExecute()) {
                return jcmdFile.getAbsolutePath();
            }
            File jcmdExe = new File(bin, "jcmd.exe");
            if (jcmdExe.exists() && jcmdExe.canExecute()) {
                return jcmdExe.getAbsolutePath();
            }
        }

        // Fallback 3: Try running 'jcmd' directly to see if it is in system PATH
        try {
            Process p = new ProcessBuilder("jcmd", "-l").start();
            p.destroy();
            return "jcmd";
        } catch (IOException e) {
            // jcmd is not in system PATH
        }

        return null;
    }

    private void refreshRam() {
        String jcmdPath = jcmdCommandPath;
        Process activeProcess = process;
        if (jcmdPath == null || activeProcess == null || !activeProcess.isAlive()) {
            return;
        }

        Process jcmdProcess = null;
        try {
            long pid;
            try {
                pid = activeProcess.pid();
            } catch (IllegalStateException e) {
                return;
            }
            jcmdProcess = new ProcessBuilder(jcmdPath, String.valueOf(pid), "GC.heap_info")
                    .redirectErrorStream(true)
                    .start();

            // H2 fix: önce timeout ile bekle, sonra stream oku — aksi halde readAllBytes
            // jcmd hang'inde executor thread'ini kalıcı bloke ediyordu
            boolean finished = jcmdProcess.waitFor(3, TimeUnit.SECONDS);
            String output;
            try (InputStream input = jcmdProcess.getInputStream()) {
                if (finished) {
                    output = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                } else {
                    jcmdProcess.destroyForcibly();
                    return;
                }
            }

            // Parse heap info
            lastRamSnapshot = parseHeapInfo(output);

        } catch (IOException e) {
            // Silent fail - background task
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (jcmdProcess != null && jcmdProcess.isAlive()) {
                jcmdProcess.destroyForcibly();
            }
        }
    }

    /**
     * Parses jcmd GC.heap_info output to extract used/max heap.
     * Typical jcmd output format:
     *   garbage-first heap   total 262144K, used 45678K ...
     *   or: Heap Usage: ... used = 12345678 (12MB) ...
     */
    private String parseHeapInfo(String output) {
        if (output == null || output.isBlank()) {
            return "?";
        }

        try {
            // Pattern 1: "used 12345K" or "used 123M" or "used 1G"
            java.util.regex.Matcher usedMatcher = HEAP_USED_PATTERN.matcher(output);
            if (usedMatcher.find()) {
                long usedBytes = parseHeapValue(usedMatcher.group(1), usedMatcher.group(2));

                if (configuredMaxRamBytes > 0) {
                    double usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0);
                    double maxGB = configuredMaxRamBytes / (1024.0 * 1024.0 * 1024.0);
                    return String.format("%.1f/%.1fGB", usedGB, maxGB);
                } else {
                    double usedMB = usedBytes / (1024.0 * 1024.0);
                    return String.format("%.0f MB", usedMB);
                }
            }

            // Pattern 2: Look for raw byte values like "used = 123456789"
            java.util.regex.Pattern rawPattern = Pattern.compile("used\\s*=\\s*(\\d+)");
            java.util.regex.Matcher rawMatcher = rawPattern.matcher(output);
            if (rawMatcher.find()) {
                long usedBytes = Long.parseLong(rawMatcher.group(1));
                if (configuredMaxRamBytes > 0) {
                    double usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0);
                    double maxGB = configuredMaxRamBytes / (1024.0 * 1024.0 * 1024.0);
                    return String.format("%.1f/%.1fGB", usedGB, maxGB);
                } else {
                    double usedMB = usedBytes / (1024.0 * 1024.0);
                    return String.format("%.0f MB", usedMB);
                }
            }
        } catch (Exception e) {
            // Parsing failed, return unknown
        }

        return "?";
    }

    private long parseHeapValue(String numberStr, String unit) {
        long value = Long.parseLong(numberStr);
        return switch (Character.toUpperCase(unit.charAt(0))) {
            case 'K' -> value * 1024L;
            case 'M' -> value * 1024L * 1024L;
            case 'G' -> value * 1024L * 1024L * 1024L;
            default -> value;
        };
    }

    private int readMaxPlayersFromConfig() {
        try {
            Path configPath = Path.of(serverDir, "server.properties");
            if (!Files.exists(configPath)) {
                return 20;
            }

            List<String> lines = readAllLinesWithFallback(configPath);
            for (String line : lines) {
                if (line.startsWith("max-players=")) {
                    return Integer.parseInt(line.substring("max-players=".length()).trim());
                }
            }
        } catch (IOException | NumberFormatException e) {
            // Fallback to default
        }

        return 20;
    }

    /**
     * Legacy API support for backward compatibility.
     * Returns recent error details as a list of objects with getCause(), getFix(),
     * etc.
     */
    public List<RecentErrorDetail> getRecentErrorDetails() {
        return recentErrorDetails.stream()
                .map(errorEntry -> {
                    String cause = switch (errorEntry.error()) {
                        case ServerError.CrashError crashError -> crashError.message();
                        case ServerError.BindError bindError -> "Bind issue: " + bindError.address();
                        case ServerError.OutOfMemoryError memError ->
                            "Out of memory: " + memError.heapSize() + " bytes";
                        case ServerError.LogError logError ->
                            "[" + logError.exceptionName() + "] " + logError.message();
                    };

                    String title = switch (errorEntry.error()) {
                        case ServerError.CrashError ignored -> "Server Crash";
                        case ServerError.BindError ignored -> "Port Binding Failed";
                        case ServerError.OutOfMemoryError ignored -> "Out of Memory";
                        case ServerError.LogError ignored -> "Logged Error";
                    };

                    String fix = switch (errorEntry.error()) {
                        case ServerError.CrashError ignored -> "Check logs and configuration for crash reasons.";
                        case ServerError.BindError ignored -> "Stop the other process running on this port, or change server-port in server.properties.";
                        case ServerError.OutOfMemoryError ignored -> "Increase allocated RAM in sunucu_baslat.bat (-Xmx).";
                        case ServerError.LogError ignored -> "Review stack traces and debug with plugin author if necessary.";
                    };

                    String rawLine = errorEntry.rawMessage();
                    String summary = title + " - " + cause;
                    return new RecentErrorDetail(summary, cause, fix, rawLine, title);
                })
                .limit(MAX_ERROR_HISTORY)
                .collect(Collectors.toList());
    }

    /**
     * Legacy API support for backward compatibility.
     * Requests TPS update (no-op in new implementation as TPS is auto-updated).
     */
    public void requestTps() {
        if (status == Status.RUNNING) {
            sendCommand("tps");
        }
    }

    /**
     * Legacy API support for backward compatibility.
     * Returns allocated RAM as string.
     */
    public String getAllocatedRam() {
        return getRamUsage();
    }

    private void stopRamMonitor() {
        ScheduledExecutorService executor = ramRefreshExecutor;
        if (executor != null) {
            executor.shutdownNow();
            ramRefreshExecutor = null;
        }
    }

    private void closeServerResources() {
        closeServerResources(process, stdin);
    }

    private void closeServerResources(Process targetProcess, BufferedWriter targetStdin) {
        if (targetStdin != null) {
            try {
                targetStdin.close();
            } catch (IOException ignored) {
            }
        }

        synchronized (lifecycleLock) {
            // H3 fix: sadece aktif process eşleşiyorsa RAM monitor'u durdur
            // aksi halde eski reader thread yeni sunucunun executor'ını kapatıyor
            boolean isActiveProcess = (process == targetProcess);
            if (isActiveProcess) {
                stopRamMonitor();
                lastRamSnapshot = "?";
            }
            if (stdin == targetStdin) {
                stdin = null;
            }
            if (isActiveProcess) {
                process = null;
            }
        }
    }

    private String formatDuration(long millis) {
        long hours = millis / 3_600_000L;
        long minutes = (millis % 3_600_000L) / 60_000L;
        long seconds = (millis % 60_000L) / 1_000L;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
