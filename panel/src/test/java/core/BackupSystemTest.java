package core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.UUID;

/**
 * Backup sisteminin davranışlarını test eden sınıf.
 * JUnit 5 ile modern test framework kullanıyoruz.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.SAME_THREAD)
public class BackupSystemTest {

    private String testServerDir;
    private String testBackupDir;

    private BackupManager backupManager;
    private TestBackupEventListener eventListener;

    @BeforeEach
    void setup() throws IOException {
        // Reset singleton instance for test isolation
        BackupManager.resetInstance();
        ServerManager.resetInstance();

        // Oluştur unique test directory (her test kendi dizininde çalışsın)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String random = UUID.randomUUID().toString().substring(0, 8);
        testServerDir = "test_server_" + timestamp + "_" + random;
        testBackupDir = testServerDir + File.separator + "backups";

        // Create test server directory
        Files.createDirectories(Paths.get(testServerDir));

        // Create some test files in the server directory
        createTestFiles();

        // Create backup manager instance
        backupManager = BackupManager.getInstance(testServerDir);

        // Create and register event listener
        eventListener = new TestBackupEventListener();
        backupManager.addBackupEventListener(eventListener);
    }

    @AfterEach
    void cleanup() throws IOException {
        if (testServerDir != null && Files.exists(Paths.get(testServerDir))) {
            deleteDirectory(Paths.get(testServerDir));
        }
    }

    /**
     * Test 1: Backup creation with default strategy.
     */
    @Test
    @Order(1)
    @DisplayName("Test backup creation with default strategy")
    void testCreateBackup() throws IOException {
        System.out.println("\n=== Test 1: Backup Creation ===");

        System.out.println("Creating backup with default strategy...");
        IBackupStrategy.BackupEntry backupEntry = backupManager.createBackup();

        System.out.println("Backup created successfully!");
        System.out.println("Backup file: " + backupEntry.fileName());
        System.out.println("Backup size: " + backupEntry.sizeBytes() + " bytes");
        System.out.println("Backup strategy: " + backupEntry.strategyName());
        System.out.println("Backup creation time: " + new Date(backupEntry.creationTime()));

        // Check event listener
        System.out.println("\nEvent listener statistics:");
        System.out.println("Backup started events: " + eventListener.backupStartedCount);
        System.out.println("Backup completed events: " + eventListener.backupCompletedCount);

        // Verify backup file exists
        Path backupPath = Paths.get(testBackupDir, backupEntry.fileName());
        Assertions.assertTrue(Files.exists(backupPath), "Backup file should exist");
        System.out.println("Backup file exists: YES");
        System.out.println("Backup file size: " + Files.size(backupPath) + " bytes");
    }

    /**
     * Test 2: List backups.
     */
    @Test
    @Order(2)
    @DisplayName("Test listing backups")
    void testListBackups() throws IOException {
        System.out.println("\n=== Test 2: List Backups ===");

        try {
            // Create a few backups first
            System.out.println("Creating 3 backups...");
            List<IBackupStrategy.BackupEntry> createdBackups = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                IBackupStrategy.BackupEntry backup = backupManager.createBackup();
                createdBackups.add(backup);
                System.out.println("Created backup " + (i + 1) + ": " + backup.fileName());
                Thread.sleep(100); // Small delay to ensure different timestamps
            }

            // List all backups
            System.out.println("\nListing all backups:");
            List<IBackupStrategy.BackupEntry> allBackups = backupManager.listBackups();

            Assertions.assertFalse(allBackups.isEmpty(), "Should have backups created");
            System.out.println("Total backups found: " + allBackups.size());
            for (int i = 0; i < allBackups.size(); i++) {
                IBackupStrategy.BackupEntry backup = allBackups.get(i);
                System.out.println((i + 1) + ". " + backup.fileName() +
                        " (" + backup.sizeBytes() + " bytes, " +
                        new Date(backup.creationTime()) + ")");
            }

            // Verify backups are sorted by creation time (newest first)
            boolean sorted = true;
            for (int i = 0; i < allBackups.size() - 1; i++) {
                if (allBackups.get(i).creationTime() < allBackups.get(i + 1).creationTime()) {
                    sorted = false;
                    break;
                }
            }

            System.out.println("\nBackups sorted by creation time (newest first): " +
                    (sorted ? "YES" : "NO - ERROR!"));
            Assertions.assertTrue(sorted, "Backups should be sorted by creation time (newest first)");

        } catch (Exception e) {
            System.out.println("List backups test failed: " + e.getMessage());
            e.printStackTrace();
            Assertions.fail("List backups test failed: " + e.getMessage());
        }
    }

    /**
     * Test 3: Get backup info.
     */
    @Test
    @Order(3)
    @DisplayName("Test getting backup info")
    void testGetBackupInfo() throws IOException {
        System.out.println("\n=== Test 3: Get Backup Info ===");

        try {
            // Create a backup
            System.out.println("Creating a backup...");
            IBackupStrategy.BackupEntry backupEntry = backupManager.createBackup();

            // Get backup info
            System.out.println("Getting backup info for: " + backupEntry.fileName());
            IBackupStrategy.BackupInfo backupInfo = backupManager.getBackupInfo(backupEntry);

            System.out.println("Backup info retrieved successfully!");
            System.out.println("Original size: " + backupInfo.originalSize() + " bytes");
            System.out.println("Compressed size: " + backupInfo.compressedSize() + " bytes");
            System.out
                    .println("Compression ratio: " + String.format("%.1f%%", backupInfo.compressionRatio() * 100) + "");
            System.out.println("Worlds included: " + backupInfo.includedWorlds().size());
            System.out.println("Created: " + new Date(backupInfo.entry().creationTime()));

            // Verify compression ratio makes sense
            Assertions.assertTrue(backupInfo.compressionRatio() >= 0 && backupInfo.compressionRatio() <= 100,
                    "Compression ratio should be between 0 and 100");
            System.out.println("Compression ratio valid: YES");

        } catch (IOException e) {
            System.out.println("Get backup info test failed: " + e.getMessage());
            e.printStackTrace();
            Assertions.fail("Get backup info test failed: " + e.getMessage());
        }
    }

    /**
     * Test 4: Delete backup.
     */
    @Test
    @Order(4)
    @DisplayName("Test deleting backup")
    void testDeleteBackup() throws IOException {
        System.out.println("\n=== Test 4: Delete Backup ===");

        try {
            // Create a backup
            System.out.println("Creating a backup...");
            IBackupStrategy.BackupEntry backupEntry = backupManager.createBackup();
            System.out.println("Backup entry created: " + (backupEntry != null ? backupEntry.fileName() : "NULL"));
            if (backupEntry != null) {
                System.out.println("Backup entry details: size=" + backupEntry.sizeBytes() + ", time="
                        + backupEntry.creationTime());
            }

            // Check immediately after creation
            Path backupPath = Paths.get(testBackupDir, backupEntry.fileName());
            boolean existsImmediately = Files.exists(backupPath);
            System.out.println("Backup file exists immediately after creation: " + existsImmediately);
            System.out.println("Backup path: " + backupPath.toAbsolutePath());

            // Verify backup exists
            boolean existsBefore = Files.exists(backupPath);
            System.out.println("Backup exists before deletion: " + existsBefore);
            System.out.println("Expected backup path: " + backupPath.toAbsolutePath());
            System.out.println("Backup dir exists: " + Files.exists(Paths.get(testBackupDir)));

            // List files in backup dir
            if (Files.exists(Paths.get(testBackupDir))) {
                try (var stream = Files.list(Paths.get(testBackupDir))) {
                    System.out.println("Files in backup dir: " + stream.map(Path::getFileName).toList());
                }
            }

            Assertions.assertTrue(existsBefore, "Backup file should exist before deletion");

            // Delete the backup
            System.out.println("Deleting backup: " + backupEntry.fileName());
            backupManager.deleteBackup(backupEntry);

            // Verify backup deleted
            boolean existsAfter = Files.exists(backupPath);
            System.out.println("Backup exists after deletion: " + existsAfter);

            // Check event listener
            System.out.println("Backup deleted events: " + eventListener.backupDeletedCount);

            Assertions.assertFalse(existsAfter, "Backup file should not exist after deletion");
            Assertions.assertTrue(eventListener.backupDeletedCount > 0, "Backup deleted event should be fired");
            System.out.println("Backup deletion successful: YES");

        } catch (Exception e) {
            System.out.println("Delete backup test failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Test 5: Restore backup.
     */
    @Test
    @Order(5)
    @DisplayName("Test restoring backup")
    void testRestoreBackup() throws IOException {
        System.out.println("\n=== Test 5: Restore Backup ===");

        try {
            // Create a backup
            System.out.println("Creating a backup...");
            IBackupStrategy.BackupEntry backupEntry = backupManager.createBackup();

            // Modify the original files to simulate data loss
            System.out.println("Modifying original files to simulate data loss...");
            modifyTestFiles();

            // Restore the backup
            System.out.println("Restoring backup: " + backupEntry.fileName());
            backupManager.restoreBackup(backupEntry);

            System.out.println("Backup restored successfully!");

            // Check event listener
            System.out.println("Restore started events: " + eventListener.restoreStartedCount);
            System.out.println("Restore completed events: " + eventListener.restoreCompletedCount);

            // Verify files were restored (simplified check)
            System.out.println("Verifying restoration...");
            boolean restored = verifyTestFilesRestored();
            System.out.println("Files restored correctly: " + (restored ? "YES" : "NO - ERROR!"));
            Assertions.assertTrue(restored, "Files should be restored correctly");

        } catch (IOException e) {
            System.out.println("Restore backup test failed: " + e.getMessage());
            e.printStackTrace();
            Assertions.fail("Restore backup test failed: " + e.getMessage());
        }
    }

    /**
     * Test 6: Backup statistics.
     */
    @Test
    @Order(6)
    @DisplayName("Test backup statistics")
    void testBackupStats() throws Exception {
        System.out.println("\n=== Test 6: Backup Statistics ===");

        try {
            // Create multiple backups
            System.out.println("Creating 2 backups...");
            backupManager.createBackup();
            Thread.sleep(50);
            backupManager.createBackup();
            Thread.sleep(50);

            // Get statistics
            BackupManager.BackupStats stats = backupManager.getBackupStats();

            System.out.println("Backup statistics:");
            System.out.println("Total backups: " + stats.totalBackups());
            System.out.println("Total size: " + stats.getFormattedTotalSize());
            System.out.println("Oldest backup: " + stats.getOldestBackupDate());
            System.out.println("Newest backup: " + stats.getNewestBackupDate());

            // Verify statistics
            int expectedCount = 2;
            Assertions.assertEquals(expectedCount, stats.totalBackups(),
                    "Total backups count should be " + expectedCount);
            System.out.println("Total backups count correct: YES");

        } catch (Exception e) {
            System.out.println("Backup stats test failed: " + e.getMessage());
            e.printStackTrace();
            Assertions.fail("Backup stats test failed: " + e.getMessage());
        }
    }

    // Helper methods

    private void createTestFiles() throws IOException {
        // Create world directory
        Files.createDirectories(Paths.get(testServerDir, "world"));

        // Create server.properties
        String properties = """
                #Minecraft server properties
                max-players=20
                gamemode=survival
                difficulty=normal
                level-type=default
                """;
        Files.writeString(Paths.get(testServerDir, "server.properties"), properties);

        // Create bukkit.yml
        String bukkitConfig = """
                settings:
                  allow-end: true
                  warn-on-overload: true
                spawn-limits:
                  monsters: 70
                  animals: 15
                """;
        Files.writeString(Paths.get(testServerDir, "bukkit.yml"), bukkitConfig);

        // Create some world files
        Files.createDirectories(Paths.get(testServerDir, "world", "region"));
        Files.writeString(Paths.get(testServerDir, "world", "level.dat"), "World data");
        Files.writeString(Paths.get(testServerDir, "world", "region", "r.0.0.mca"), "Region data");

        System.out.println("Created test files in server directory.");
    }

    private void modifyTestFiles() throws IOException {
        // Modifytest files
        String modifiedProperties = """
                #Minecraft server properties - MODIFIED
                max-players=50
                gamemode=creative
                difficulty=hard
                level-type=flat
                """;
        Files.writeString(Paths.get(testServerDir, "server.properties"), modifiedProperties);

        System.out.println("Modified test files to simulate data loss.");
    }

    private boolean verifyTestFilesRestored() {
        try {
            Path propertiesFile = Paths.get(testServerDir, "server.properties");
            if (!Files.exists(propertiesFile)) {
                return false;
            }
            String content = Files.readString(propertiesFile);
            return content.contains("max-players=20") &&
                    Files.exists(Paths.get(testServerDir, "bukkit.yml")) &&
                    Files.exists(Paths.get(testServerDir, "world", "level.dat"));
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    /**
     * Test backup event listener for tracking events.
     */
    private static class TestBackupEventListener implements BackupManager.BackupEventListener {
        int backupStartedCount = 0;
        int backupCompletedCount = 0;
        int backupFailedCount = 0;
        int backupDeletedCount = 0;
        int restoreStartedCount = 0;
        int restoreCompletedCount = 0;
        int restoreFailedCount = 0;

        @Override
        public void onBackupStarted() {
            backupStartedCount++;
            System.out.println("[EventListener] Backup started");
        }

        @Override
        public void onBackupCompleted(BackupManager.BackupEvent event) {
            backupCompletedCount++;
            System.out.println("[EventListener] Backup completed: " +
                    event.backupEntry().fileName() + " (" + event.durationMillis() + "ms)");
        }

        @Override
        public void onBackupFailed(BackupManager.BackupEvent event) {
            backupFailedCount++;
            System.out.println("[EventListener] Backup failed: " + event.errorMessage());
        }

        @Override
        public void onBackupDeleted(BackupManager.BackupEvent event) {
            backupDeletedCount++;
            System.out.println("[EventListener] Backup deleted: " +
                    event.backupEntry().fileName());
        }

        @Override
        public void onRestoreStarted() {
            restoreStartedCount++;
            System.out.println("[EventListener] Restore started");
        }

        @Override
        public void onRestoreCompleted(BackupManager.BackupEvent event) {
            restoreCompletedCount++;
            System.out.println("[EventListener] Restore completed: " +
                    event.backupEntry().fileName() + " (" + event.durationMillis() + "ms)");
        }

        @Override
        public void onRestoreFailed(BackupManager.BackupEvent event) {
            restoreFailedCount++;
            System.out.println("[EventListener] Restore failed: " + event.errorMessage());
        }
    }

}