package core;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Modern backup manager with Strategy Pattern and Factory Pattern.
 * Supports multiple backup strategies and provides a clean API.
 */
public final class BackupManager {

    // Singleton instance
    private static volatile BackupManager instance;

    private final String serverDir;
    private final String backupDir;
    private final Map<String, IBackupStrategy> strategies = new ConcurrentHashMap<>();
    private final List<BackupEventListener> listeners = new CopyOnWriteArrayList<>();
    private IBackupStrategy defaultStrategy;

    // Factory for creating strategies
    private final BackupStrategyFactory strategyFactory = new BackupStrategyFactory();

    /**
     * Private constructor for singleton pattern.
     */
    private BackupManager(String serverDir) {
        this.serverDir = Objects.requireNonNull(serverDir, "serverDir cannot be null");
        this.backupDir = serverDir + File.separator + "backups";

        // Create backup directory
        try {
            Files.createDirectories(Paths.get(backupDir));
        } catch (IOException e) {
            System.err.println("Failed to create backup directory: " + e.getMessage());
        }

        // Initialize strategies
        initializeStrategies();
    }

    /**
     * Singleton factory method.
     *
     * @throws IllegalStateException if already initialized with a different serverDir
     */
    public static BackupManager getInstance(String serverDir) {
        String resolved = Objects.requireNonNull(serverDir, "serverDir cannot be null");
        if (instance == null) {
            synchronized (BackupManager.class) {
                if (instance == null) {
                    instance = new BackupManager(resolved);
                }
            }
        } else if (!instance.serverDir.equals(resolved)) {
            throw new IllegalStateException(
                    "BackupManager zaten '" + instance.serverDir + "' için başlatılmış. Yeni istek: '" + resolved + "'");
        }
        return instance;
    }

    /**
     * Resets the singleton instance (for testing).
     */
    static void resetInstance() {
        instance = null;
    }

    /**
     * Creates a backup using the default strategy.
     * 
     * @return The created backup entry
     * @throws IOException If backup creation fails
     */
    public IBackupStrategy.BackupEntry createBackup() throws IOException {
        return createBackup(defaultStrategy);
    }

    /**
     * Creates a backup using a specific strategy.
     * 
     * @param strategyName The name of the strategy to use
     * @return The created backup entry
     * @throws IOException If backup creation fails
     */
    public IBackupStrategy.BackupEntry createBackup(String strategyName) throws IOException {
        IBackupStrategy strategy = strategies.get(strategyName.toUpperCase(java.util.Locale.ROOT));
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown backup strategy: " + strategyName);
        }
        return createBackup(strategy);
    }

    /**
     * Creates a backup using the provided strategy.
     * 
     * @param strategy The strategy to use
     * @return The created backup entry
     * @throws IOException If backup creation fails
     */
    private IBackupStrategy.BackupEntry createBackup(IBackupStrategy strategy) throws IOException {
        // Notify listeners backup is starting
        fireBackupEvent(BackupEvent.Type.STARTED, null);

        long startTime = System.currentTimeMillis();
        ServerManager server = ServerManager.getInstance(serverDir);
        boolean wasRunning = server.isRunning();

        if (wasRunning) {
            server.sendCommand("save-off");
            server.sendCommand("save-all");
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            // Create backup
            String fileName = strategy.createBackup(serverDir, backupDir);

            // Get backup info
            Path backupPath = Paths.get(backupDir, fileName);
            BasicFileAttributes attrs = Files.readAttributes(backupPath, BasicFileAttributes.class);

            IBackupStrategy.BackupEntry entry = new IBackupStrategy.BackupEntry(
                    fileName,
                    attrs.size(),
                    attrs.creationTime().toMillis(),
                    strategy.getClass().getSimpleName().replace("BackupStrategy", ""));

            long duration = System.currentTimeMillis() - startTime;

            // Notify listeners backup completed
            fireBackupEvent(BackupEvent.Type.COMPLETED, new BackupEvent(
                    BackupEvent.Type.COMPLETED,
                    entry,
                    duration,
                    null));

            return entry;

        } catch (IOException e) {
            // Notify listeners backup failed
            fireBackupEvent(BackupEvent.Type.FAILED, new BackupEvent(
                    BackupEvent.Type.FAILED,
                    null,
                    System.currentTimeMillis() - startTime,
                    e.getMessage()));

            throw e;
        } finally {
            if (wasRunning) {
                server.sendCommand("save-on");
            }
        }
    }

    /**
     * Lists all available backups.
     * 
     * @return List of backup entries sorted by creation time (newest first)
     */
    public List<IBackupStrategy.BackupEntry> listBackups() {
        List<IBackupStrategy.BackupEntry> allBackups = new ArrayList<>();

        for (IBackupStrategy strategy : strategies.values()) {
            allBackups.addAll(strategy.listBackups(backupDir));
        }

        // Sort by creation time (newest first)
        allBackups.sort((a, b) -> Long.compare(b.creationTime(), a.creationTime()));

        return allBackups;
    }

    /**
     * Lists backups created with a specific strategy.
     * 
     * @param strategyName The name of the strategy
     * @return List of backup entries
     */
    public List<IBackupStrategy.BackupEntry> listBackups(String strategyName) {
        IBackupStrategy strategy = strategies.get(strategyName);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown backup strategy: " + strategyName);
        }

        return strategy.listBackups(backupDir);
    }

    /**
     * Deletes a backup.
     * 
     * @param backupEntry The backup entry to delete
     * @throws IOException If backup deletion fails
     */
    public void deleteBackup(IBackupStrategy.BackupEntry backupEntry) throws IOException {
        Objects.requireNonNull(backupEntry, "backupEntry cannot be null");

        // Find the strategy that created this backup
        IBackupStrategy strategy = strategies.get(backupEntry.strategyName().toUpperCase(java.util.Locale.ROOT));
        if (strategy == null) {
            throw new IllegalArgumentException("Backup strategy not found: " + backupEntry.strategyName());
        }

        strategy.deleteBackup(backupDir, backupEntry);

        // Notify listeners backup deleted
        fireBackupEvent(BackupEvent.Type.DELETED, new BackupEvent(
                BackupEvent.Type.DELETED,
                backupEntry,
                0,
                null));
    }

    /**
     * Gets detailed information about a backup.
     * 
     * @param backupEntry The backup entry
     * @return Detailed backup information
     * @throws IOException If backup info retrieval fails
     */
    public IBackupStrategy.BackupInfo getBackupInfo(IBackupStrategy.BackupEntry backupEntry) throws IOException {
        Objects.requireNonNull(backupEntry, "backupEntry cannot be null");

        // Find the strategy that created this backup
        IBackupStrategy strategy = strategies.get(backupEntry.strategyName().toUpperCase(java.util.Locale.ROOT));
        if (strategy == null) {
            throw new IllegalArgumentException("Backup strategy not found: " + backupEntry.strategyName());
        }

        return strategy.getBackupInfo(backupDir, backupEntry);
    }

    /**
     * Restores a backup.
     * 
     * @param backupEntry The backup entry to restore
     * @throws IOException If restore fails
     */
    public void restoreBackup(IBackupStrategy.BackupEntry backupEntry) throws IOException {
        Objects.requireNonNull(backupEntry, "backupEntry cannot be null");

        ServerManager serverMgr = ServerManager.getInstance(serverDir);
        if (serverMgr.getStatus() != IServerManager.Status.STOPPED) {
            throw new IllegalStateException(Lang.get("WARN_RESTORE_RUNNING"));
        }

        // Find the strategy that created this backup
        IBackupStrategy strategy = strategies.get(backupEntry.strategyName().toUpperCase(java.util.Locale.ROOT));
        if (strategy == null) {
            throw new IllegalArgumentException("Backup strategy not found: " + backupEntry.strategyName());
        }

        // Notify listeners restore is starting
        fireBackupEvent(BackupEvent.Type.RESTORE_STARTED, null);

        long startTime = System.currentTimeMillis();

        try {
            strategy.restoreBackup(serverDir, backupDir, backupEntry);

            long duration = System.currentTimeMillis() - startTime;

            // Notify listeners restore completed
            fireBackupEvent(BackupEvent.Type.RESTORE_COMPLETED, new BackupEvent(
                    BackupEvent.Type.RESTORE_COMPLETED,
                    backupEntry,
                    duration,
                    null));

        } catch (IOException e) {
            // Notify listeners restore failed
            fireBackupEvent(BackupEvent.Type.RESTORE_FAILED, new BackupEvent(
                    BackupEvent.Type.RESTORE_FAILED,
                    backupEntry,
                    System.currentTimeMillis() - startTime,
                    e.getMessage()));

            throw e;
        }
    }

    /**
     * Gets the default backup strategy.
     * 
     * @return The default strategy
     */
    public IBackupStrategy getDefaultStrategy() {
        return defaultStrategy;
    }

    /**
     * Sets the default backup strategy.
     * 
     * @param strategyName The name of the strategy to set as default
     */
    public void setDefaultStrategy(String strategyName) {
        IBackupStrategy strategy = strategies.get(strategyName);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown backup strategy: " + strategyName);
        }
        this.defaultStrategy = strategy;
    }

    /**
     * Gets all available backup strategies.
     * 
     * @return Map of strategy names to strategies
     */
    public Map<String, IBackupStrategy> getStrategies() {
        return Map.copyOf(strategies);
    }

    /**
     * Adds a backup event listener.
     * 
     * @param listener The listener to add
     */
    public void addBackupEventListener(BackupEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a backup event listener.
     * 
     * @param listener The listener to remove
     */
    public void removeBackupEventListener(BackupEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Gets backup statistics.
     * 
     * @return Statistics about backups
     */
    public BackupStats getBackupStats() {
        List<IBackupStrategy.BackupEntry> allBackups = listBackups();

        long totalSize = allBackups.stream()
                .mapToLong(IBackupStrategy.BackupEntry::sizeBytes)
                .sum();

        Map<String, Long> strategyCounts = new HashMap<>();
        for (IBackupStrategy.BackupEntry entry : allBackups) {
            strategyCounts.merge(entry.strategyName(), 1L, Long::sum);
        }

        Optional<IBackupStrategy.BackupEntry> oldest = allBackups.stream()
                .min(Comparator.comparingLong(IBackupStrategy.BackupEntry::creationTime));

        Optional<IBackupStrategy.BackupEntry> newest = allBackups.stream()
                .max(Comparator.comparingLong(IBackupStrategy.BackupEntry::creationTime));

        return new BackupStats(
                allBackups.size(),
                totalSize,
                strategyCounts,
                oldest.map(IBackupStrategy.BackupEntry::creationTime).orElse(0L),
                newest.map(IBackupStrategy.BackupEntry::creationTime).orElse(0L));
    }

    // Private helper methods

    private void initializeStrategies() {
        // Create strategies using factory
        IBackupStrategy zipStrategy = strategyFactory.createStrategy("ZIP");
        IBackupStrategy tarStrategy = strategyFactory.createStrategy("TAR");
        IBackupStrategy fastStrategy = strategyFactory.createStrategy("FAST");

        strategies.put("ZIP", zipStrategy);
        strategies.put("TAR", tarStrategy);
        strategies.put("FAST", fastStrategy);

        // Set default strategy
        defaultStrategy = zipStrategy;
    }

    private void fireBackupEvent(BackupEvent.Type type, BackupEvent event) {
        for (BackupEventListener listener : listeners) {
            try {
                switch (type) {
                    case STARTED -> listener.onBackupStarted();
                    case COMPLETED -> listener.onBackupCompleted(event);
                    case FAILED -> listener.onBackupFailed(event);
                    case DELETED -> listener.onBackupDeleted(event);
                    case RESTORE_STARTED -> listener.onRestoreStarted();
                    case RESTORE_COMPLETED -> listener.onRestoreCompleted(event);
                    case RESTORE_FAILED -> listener.onRestoreFailed(event);
                }
            } catch (Exception e) {
                System.err.println("Error in backup event listener: " + e.getMessage());
            }
        }
    }

    // Inner classes

    /**
     * Factory for creating backup strategies.
     */
    public static class BackupStrategyFactory {

        /**
         * Creates a backup strategy by name.
         * 
         * @param strategyName The name of the strategy
         * @return The created strategy
         * @throws IllegalArgumentException If the strategy name is unknown
         */
        public IBackupStrategy createStrategy(String strategyName) {
            return switch (strategyName.toUpperCase(java.util.Locale.ROOT)) {
                case "ZIP" -> new ZipBackupStrategy();
                case "TAR" -> new TarBackupStrategy();
                case "FAST" -> new FastBackupStrategy();
                default -> throw new IllegalArgumentException(
                        "Unknown backup strategy: " + strategyName);
            };
        }

        /**
         * Gets all available strategy names.
         * 
         * @return List of strategy names
         */
        public List<String> getAvailableStrategies() {
            return List.of("ZIP", "TAR", "FAST");
        }
    }

    /**
     * Backup event for notifying listeners.
     */
    public record BackupEvent(
            Type type,
            IBackupStrategy.BackupEntry backupEntry,
            long durationMillis,
            String errorMessage) {
        public enum Type {
            STARTED,
            COMPLETED,
            FAILED,
            DELETED,
            RESTORE_STARTED,
            RESTORE_COMPLETED,
            RESTORE_FAILED
        }
    }

    /**
     * Interface for backup event listeners.
     */
    public interface BackupEventListener {
        default void onBackupStarted() {
        }

        default void onBackupCompleted(BackupEvent event) {
        }

        default void onBackupFailed(BackupEvent event) {
        }

        default void onBackupDeleted(BackupEvent event) {
        }

        default void onRestoreStarted() {
        }

        default void onRestoreCompleted(BackupEvent event) {
        }

        default void onRestoreFailed(BackupEvent event) {
        }
    }

    /**
     * Legacy API support for backward compatibility.
     * Returns list of backup names as strings.
     */
    public List<String> listBackupNames() {
        return listBackups().stream()
                .map(IBackupStrategy.BackupEntry::fileName)
                .collect(Collectors.toList());
    }

    /**
     * Legacy API support for backward compatibility.
     * Creates a backup and returns its name as string.
     */
    public String createBackupLegacy() throws IOException {
        IBackupStrategy.BackupEntry entry = createBackup();
        return entry.fileName();
    }

    /**
     * Legacy API support for backward compatibility.
     * Deletes a backup by its name (string).
     */
    public void deleteBackup(String backupName) throws IOException {
        // Find the backup entry by name
        IBackupStrategy.BackupEntry entry = listBackups().stream()
                .filter(e -> e.fileName().equals(backupName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Backup not found: " + backupName));

        deleteBackup(entry);
    }

    /**
     * Backup statistics.
     */
    public record BackupStats(
            int totalBackups,
            long totalSizeBytes,
            Map<String, Long> strategyCounts,
            long oldestBackupTime,
            long newestBackupTime) {
        public String getFormattedTotalSize() {
            if (totalSizeBytes < 1024)
                return totalSizeBytes + " B";
            if (totalSizeBytes < 1024 * 1024)
                return String.format("%.1f KB", totalSizeBytes / 1024.0);
            if (totalSizeBytes < 1024 * 1024 * 1024)
                return String.format("%.1f MB", totalSizeBytes / (1024.0 * 1024.0));
            return String.format("%.1f GB", totalSizeBytes / (1024.0 * 1024.0 * 1024.0));
        }

        public String getOldestBackupDate() {
            if (oldestBackupTime == 0)
                return "N/A";
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date(oldestBackupTime));
        }

        public String getNewestBackupDate() {
            if (newestBackupTime == 0)
                return "N/A";
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date(newestBackupTime));
        }
    }
}