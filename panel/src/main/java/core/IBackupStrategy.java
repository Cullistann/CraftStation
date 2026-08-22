package core;

import java.io.IOException;
import java.util.List;

/**
 * Strategy Pattern interface for different backup strategies.
 * Follows Open/Closed Principle - new strategies can be added without modifying existing code.
 */
public interface IBackupStrategy {
    
    /**
     * Creates a backup using this strategy.
     * 
     * @param serverDir The server directory to backup
     * @param backupDir The directory where backups are stored
     * @return The name of the created backup file
     * @throws IOException If backup creation fails
     */
    String createBackup(String serverDir, String backupDir) throws IOException;
    
    /**
     * Lists available backups created with this strategy.
     * 
     * @param backupDir The directory where backups are stored
     * @return List of backup entries with metadata
     */
    List<BackupEntry> listBackups(String backupDir);
    
    /**
     * Deletes a backup created with this strategy.
     * 
     * @param backupDir The directory where backups are stored
     * @param backupEntry The backup entry to delete
     * @throws IOException If backup deletion fails
     */
    void deleteBackup(String backupDir, BackupEntry backupEntry) throws IOException;
    
    /**
     * Gets information about a specific backup.
     * 
     * @param backupDir The directory where backups are stored
     * @param backupEntry The backup entry to inspect
     * @return Backup information including size, creation time, etc.
     */
    BackupInfo getBackupInfo(String backupDir, BackupEntry backupEntry) throws IOException;
    
    /**
     * Restores a backup created with this strategy.
     * 
     * @param serverDir The server directory to restore to
     * @param backupDir The directory where backups are stored
     * @param backupEntry The backup entry to restore
     * @throws IOException If restore fails
     */
    void restoreBackup(String serverDir, String backupDir, BackupEntry backupEntry) throws IOException;
    
    /**
     * Record representing a backup entry with metadata.
     */
    record BackupEntry(String fileName, long sizeBytes, long creationTime, String strategyName) {
        
        public String getFormattedSize() {
            if (sizeBytes < 1024) return sizeBytes + " B";
            if (sizeBytes < 1024 * 1024) return String.format("%.1f KB", sizeBytes / 1024.0);
            if (sizeBytes < 1024 * 1024 * 1024) return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
            return String.format("%.1f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0));
        }
        
        public String getFormattedDate() {
            return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(creationTime));
        }
    }
    
    /**
     * Record representing detailed backup information.
     */
    record BackupInfo(
        BackupEntry entry,
        List<String> includedWorlds,
        long compressedSize,
        long originalSize,
        double compressionRatio,
        String checksum
    ) {
        
        public String getCompressionRatioFormatted() {
            return String.format("%.1f%%", compressionRatio * 100);
        }
        
        public String getSpaceSaved() {
            long saved = originalSize - compressedSize;
            if (saved < 1024) return saved + " B";
            if (saved < 1024 * 1024) return String.format("%.1f KB", saved / 1024.0);
            if (saved < 1024 * 1024 * 1024) return String.format("%.1f MB", saved / (1024.0 * 1024.0));
            return String.format("%.1f GB", saved / (1024.0 * 1024.0 * 1024.0));
        }
    }
}